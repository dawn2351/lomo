package com.lomo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseTransitionStrategyTest {
    private val migrationEdges = listOf(18 to 19, 19 to 20)
    private val migrations =
        listOf(
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) = Unit
            },
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) = Unit
            },
        )

    @Test
    fun canReachTargetVersion_returnsTrue_forTransitivePath() {
        val result =
            DatabaseTransitionStrategy.canReachTargetVersion(
                fromVersion = 18,
                targetVersion = 20,
                migrationEdges = migrationEdges,
            )

        assertTrue(result)
    }

    @Test
    fun canReachTargetVersion_returnsFalse_forDowngradeDirection() {
        val result =
            DatabaseTransitionStrategy.canReachTargetVersion(
                fromVersion = 20,
                targetVersion = 19,
                migrationEdges = migrationEdges,
            )

        assertFalse(result)
    }

    @Test
    fun shouldResetDatabase_returnsTrue_forUnknownVersion() {
        val result =
            DatabaseTransitionStrategy.shouldResetDatabase(
                existingVersion = -1,
                targetVersion = 20,
                migrationEdges = migrationEdges,
            )

        assertTrue(result)
    }

    @Test
    fun shouldResetDatabase_returnsFalse_forSameVersion() {
        val result =
            DatabaseTransitionStrategy.shouldResetDatabase(
                existingVersion = 20,
                targetVersion = 20,
                migrationEdges = migrationEdges,
            )

        assertFalse(result)
    }

    @Test
    fun shouldResetDatabase_returnsFalse_whenMigrationPathExists() {
        val result =
            DatabaseTransitionStrategy.shouldResetDatabase(
                existingVersion = 18,
                targetVersion = 20,
                migrationEdges = migrationEdges,
            )

        assertFalse(result)
    }

    @Test
    fun shouldResetDatabase_returnsTrue_whenNoMigrationPath() {
        val result =
            DatabaseTransitionStrategy.shouldResetDatabase(
                existingVersion = 17,
                targetVersion = 20,
                migrationEdges = migrationEdges,
            )

        assertTrue(result)
    }

    @Test
    fun fallbackToDestructiveFromVersions_generatesLegacyRangeFromMigrations() {
        val result =
            DatabaseTransitionStrategy.fallbackToDestructiveFromVersions(
                migrations = migrations,
                targetVersion = 20,
            )

        assertArrayEquals((1..17).toList().toIntArray(), result)
    }

    @Test
    fun fallbackToDestructiveFromVersions_isEmpty_whenMigrationsStartAt1() {
        val startAtOne =
            listOf(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) = Unit
                },
            )

        val result =
            DatabaseTransitionStrategy.fallbackToDestructiveFromVersions(
                migrations = startAtOne,
                targetVersion = 2,
            )

        assertArrayEquals(intArrayOf(), result)
    }
}
