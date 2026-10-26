package com.creditengine.infrastructure.persistence.report

import com.creditengine.domain.model.PaymentCurrency
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class SettlementReportRow(
    val id: String,
    val receivableId: String,
    val assignorId: String,
    val faceValue: String,
    val presentValueBrl: String,
    val discountBrl: String,
    val paymentAmount: String,
    val paymentCurrency: String,
    val fxRateUsdBrl: String?,
    val settledAt: Instant,
)

@Repository
class SettlementReportRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun findPage(
        from: Instant?,
        to: Instant?,
        assignorId: String?,
        paymentCurrency: PaymentCurrency?,
        page: Int,
        size: Int,
    ): Pair<List<SettlementReportRow>, Long> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        if (from != null) {
            conditions += "s.settled_at >= :from"
            params["from"] = Timestamp.from(from)
        }
        if (to != null) {
            conditions += "s.settled_at <= :to"
            params["to"] = Timestamp.from(to)
        }
        if (assignorId != null) {
            conditions += "s.assignor_id = :assignorId"
            params["assignorId"] = assignorId
        }
        if (paymentCurrency != null) {
            conditions += "s.payment_currency = :paymentCurrency"
            params["paymentCurrency"] = paymentCurrency.name
        }

        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val countSql = "SELECT COUNT(*) FROM settlements s $where"
        val total = jdbc.queryForObject(countSql, params, Long::class.java) ?: 0L

        params["limit"] = size
        params["offset"] = page * size

        val sql = """
            SELECT s.id, s.receivable_id, s.assignor_id, s.face_value, s.present_value_brl,
                   s.discount_brl, s.payment_amount, s.payment_currency, s.fx_rate_usd_brl, s.settled_at
            FROM settlements s
            $where
            ORDER BY s.settled_at DESC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        val rows = jdbc.query(sql, params) { rs, _ ->
            SettlementReportRow(
                id = rs.getObject("id").toString(),
                receivableId = rs.getObject("receivable_id").toString(),
                assignorId = rs.getString("assignor_id"),
                faceValue = rs.getBigDecimal("face_value").toPlainString(),
                presentValueBrl = rs.getBigDecimal("present_value_brl").toPlainString(),
                discountBrl = rs.getBigDecimal("discount_brl").toPlainString(),
                paymentAmount = rs.getBigDecimal("payment_amount").toPlainString(),
                paymentCurrency = rs.getString("payment_currency"),
                fxRateUsdBrl = rs.getBigDecimal("fx_rate_usd_brl")?.toPlainString(),
                settledAt = rs.getTimestamp("settled_at").toInstant(),
            )
        }
        return rows to total
    }
}
