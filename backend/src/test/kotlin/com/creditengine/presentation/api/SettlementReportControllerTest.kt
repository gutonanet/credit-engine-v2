package com.creditengine.presentation.api

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableStatus
import com.creditengine.domain.model.ReceivableType
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.entity.SettlementEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SettlementReportControllerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("credit_engine_test")
            .withUsername("credit")
            .withPassword("credit")

        @DynamicPropertySource
        @JvmStatic
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var receivableRepository: ReceivableRepository
    @Autowired lateinit var settlementRepository: SettlementRepository

    @BeforeEach
    fun seedReport() {
        settlementRepository.deleteAll()
        receivableRepository.deleteAll()

        repeat(20) { index ->
            saveSettlement(
                assignorId = "cedente-alfa",
                currency = PaymentCurrency.BRL,
                settledAt = Instant.parse("2026-06-01T00:00:00Z").plusSeconds(index.toLong()),
            )
        }
        saveSettlement(
            assignorId = "cedente-beta",
            currency = PaymentCurrency.USD,
            settledAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
    }

    @Test
    fun `assignor and currency filters restrict the report`() {
        mockMvc.perform(get("/api/settlements/report").param("assignorId", "cedente-alfa"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(20))
            .andExpect(jsonPath("$.items.length()").value(20))
            .andExpect(jsonPath("$.items[*].assignorId", everyItem(equalTo("cedente-alfa"))))
            .andExpect(jsonPath("$.items[*].paymentCurrency", everyItem(equalTo("BRL"))))

        mockMvc.perform(get("/api/settlements/report").param("paymentCurrency", "USD"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].assignorId").value("cedente-beta"))
            .andExpect(jsonPath("$.items[0].paymentCurrency").value("USD"))
            .andExpect(jsonPath("$.items[0].paymentAmount").value("170.94"))

        mockMvc.perform(
            get("/api/settlements/report")
                .param("assignorId", "cedente-beta")
                .param("paymentCurrency", "BRL"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `first page of twenty exposes the page metadata`() {
        mockMvc.perform(
            get("/api/settlements/report")
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(21))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items.length()").value(20))
            .andExpect(jsonPath("$.items[*].assignorId", everyItem(equalTo("cedente-alfa"))))
    }

    private fun saveSettlement(assignorId: String, currency: PaymentCurrency, settledAt: Instant) {
        val receivable = receivableRepository.save(
            ReceivableEntity(
                assignorId = assignorId,
                faceValue = BigDecimal("1000.00"),
                dueDate = LocalDate.of(2027, 1, 25),
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                paymentCurrency = currency,
                status = ReceivableStatus.SETTLED,
            ),
        )
        val paymentAmount = if (currency == PaymentCurrency.USD) BigDecimal("170.94") else BigDecimal("928.59")
        settlementRepository.save(
            SettlementEntity(
                receivableId = receivable.id,
                idempotencyKey = UUID.randomUUID(),
                requestBodyHash = "a".repeat(64),
                assignorId = assignorId,
                faceValue = BigDecimal("1000.00"),
                termMonths = 3,
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                baseRate = BigDecimal("0.01000000"),
                spread = BigDecimal("0.01500000"),
                presentValueBrl = BigDecimal("928.59"),
                discountBrl = BigDecimal("71.41"),
                paymentAmount = paymentAmount,
                paymentCurrency = currency,
                fxRateUsdBrl = if (currency == PaymentCurrency.USD) BigDecimal("5.43210000") else null,
                settledAt = settledAt,
            ),
        )
    }
}
