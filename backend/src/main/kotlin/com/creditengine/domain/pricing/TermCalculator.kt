package com.creditengine.domain.pricing

import com.creditengine.domain.BusinessRuleException
import java.time.LocalDate
import java.time.ZoneId

object TermCalculator {
    private val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    fun referenceDate(now: java.time.Instant = java.time.Instant.now()): LocalDate =
        now.atZone(ZONE).toLocalDate()

    /**
     * Civil whole months by anniversary between reference and due (inclusive of full months only).
     */
    fun wholeMonthsBetween(reference: LocalDate, dueDate: LocalDate): Int {
        if (!dueDate.isAfter(reference)) {
            throw BusinessRuleException("dueDate must be after reference date")
        }
        var count = 0
        var cursor = reference
        while (true) {
            val next = cursor.plusMonths(1)
            if (next.isAfter(dueDate)) break
            count++
            cursor = next
        }
        if (count < 1) {
            throw BusinessRuleException("term must be at least 1 month")
        }
        return count
    }
}
