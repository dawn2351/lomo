package com.lomo.data.repository

import com.lomo.data.local.dao.ImageCacheDao
import com.lomo.data.local.dao.MemoDao
import com.lomo.data.local.dao.MemoTokenDao
import com.lomo.data.local.dao.PendingOpDao
import com.lomo.data.local.datastore.LomoDataStore
import com.lomo.data.local.entity.PendingOpEntity
import com.lomo.data.parser.MarkdownParser
import com.lomo.data.source.FileDataSource
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoRepositoryImplTest {
    @MockK(relaxed = true)
    private lateinit var dao: MemoDao

    @MockK(relaxed = true)
    private lateinit var imageCacheDao: ImageCacheDao

    @MockK(relaxed = true)
    private lateinit var tokenDao: MemoTokenDao

    @MockK(relaxed = true)
    private lateinit var dataSource: FileDataSource

    @MockK(relaxed = true)
    private lateinit var synchronizer: MemoSynchronizer

    @MockK(relaxed = true)
    private lateinit var parser: MarkdownParser

    @MockK(relaxed = true)
    private lateinit var dataStore: LomoDataStore

    @MockK(relaxed = true)
    private lateinit var pendingOpDao: PendingOpDao

    private lateinit var repository: MemoRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository =
            MemoRepositoryImpl(
                dao = dao,
                imageCacheDao = imageCacheDao,
                tokenDao = tokenDao,
                dataSource = dataSource,
                synchronizer = synchronizer,
                parser = parser,
                dataStore = dataStore,
                pendingOpDao = pendingOpDao,
            )
    }

    @Test
    fun `saveMemo prunes pending ops and removes inserted row on success`() =
        runTest {
            val purgeBeforeSlot = slot<Long>()
            val pendingOpSlot = slot<PendingOpEntity>()
            coEvery { pendingOpDao.deleteOlderThan(capture(purgeBeforeSlot)) } just runs
            coEvery { pendingOpDao.trimToLatest(any()) } just runs
            coEvery { pendingOpDao.insert(capture(pendingOpSlot)) } returns 42L
            coEvery { synchronizer.saveMemo(any(), any()) } just runs
            coEvery { pendingOpDao.delete(any()) } just runs

            val oversizedPayload = "x".repeat(3000)
            val before = System.currentTimeMillis()
            repository.saveMemo(oversizedPayload, timestamp = 123L)
            val after = System.currentTimeMillis()

            assertEquals("CREATE", pendingOpSlot.captured.type)
            assertEquals(2048, pendingOpSlot.captured.payload.length)
            assertTrue(pendingOpSlot.captured.timestamp in before..after)

            val ttlMs = 7L * 24 * 60 * 60 * 1000
            assertTrue(purgeBeforeSlot.captured in (before - ttlMs)..(after - ttlMs))

            coVerify(exactly = 1) { pendingOpDao.deleteOlderThan(any()) }
            coVerify(exactly = 2) { pendingOpDao.trimToLatest(500) }
            coVerify(exactly = 1) { pendingOpDao.insert(any()) }
            coVerify(exactly = 1) { synchronizer.saveMemo(oversizedPayload, 123L) }
            coVerify(exactly = 1) { pendingOpDao.delete(42L) }
        }

    @Test
    fun `saveMemo keeps pending row when sync fails`() =
        runTest {
            coEvery { pendingOpDao.deleteOlderThan(any()) } just runs
            coEvery { pendingOpDao.trimToLatest(any()) } just runs
            coEvery { pendingOpDao.insert(any()) } returns 7L
            coEvery {
                synchronizer.saveMemo(any(), any())
            } throws IllegalStateException("sync failed")

            val thrown =
                runCatching {
                    repository.saveMemo("content", timestamp = 456L)
                }.exceptionOrNull()
            assertTrue(thrown is IllegalStateException)

            coVerify(exactly = 1) { pendingOpDao.deleteOlderThan(any()) }
            coVerify(exactly = 2) { pendingOpDao.trimToLatest(500) }
            coVerify(exactly = 1) { pendingOpDao.insert(any()) }
            coVerify(exactly = 0) { pendingOpDao.delete(any()) }
        }
}
