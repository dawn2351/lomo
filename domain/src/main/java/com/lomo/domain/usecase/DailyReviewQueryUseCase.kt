package com.lomo.domain.usecase

import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.random.Random
import javax.inject.Inject

/**
 * Picks a deterministic "daily review" random sample from the full memo stream.
 *
 * For a given [seedDate], the same random order is produced so results are stable within that day,
 * while still sampling from the entire memo set instead of a contiguous window.
 */
class DailyReviewQueryUseCase
    @Inject
    constructor(
        private val repository: MemoRepository,
    ) {
        suspend operator fun invoke(
            limit: Int,
            seedDate: LocalDate,
        ): List<Memo> {
            if (limit <= 0) return emptyList()

            val allMemos = repository.getAllMemosList().first()
            if (allMemos.isEmpty()) return emptyList()

            val safeLimit = limit.coerceAtMost(allMemos.size)
            if (safeLimit == allMemos.size) return allMemos

            val dailyRandom = Random(seedDate.toEpochDay())
            return allMemos.shuffled(dailyRandom).take(safeLimit)
        }
    }
