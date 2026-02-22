package com.lomo.data.source

import android.content.Context
import com.lomo.data.local.datastore.LomoDataStore
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FileDataSourceImplTest {
    @MockK(relaxed = true)
    private lateinit var context: Context

    @MockK(relaxed = true)
    private lateinit var dataStore: LomoDataStore

    private lateinit var dataSource: FileDataSourceImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dataSource = FileDataSourceImpl(context, dataStore)
    }

    @Test
    fun `setImageRoot with file path stores directory and clears uri`() =
        runTest {
            dataSource.setImageRoot("/storage/emulated/0/Pictures/Lomo")

            coVerify(exactly = 1) { dataStore.updateImageDirectory("/storage/emulated/0/Pictures/Lomo") }
            coVerify(exactly = 1) { dataStore.updateImageUri(null) }
        }

    @Test
    fun `setImageRoot with content uri stores uri and clears directory`() =
        runTest {
            val uri = "content://com.android.externalstorage.documents/tree/primary%3APictures"

            dataSource.setImageRoot(uri)

            coVerify(exactly = 1) { dataStore.updateImageUri(uri) }
            coVerify(exactly = 1) { dataStore.updateImageDirectory(null) }
        }
}
