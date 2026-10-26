package com.creditengine.presentation.api

import com.creditengine.domain.pricing.TermCalculator
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SettlementControllerWebTest {
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
    fun cleanDatabase() {
        settlementRepository.deleteAll()
        receivableRepository.deleteAll()
    }

    @Test
    fun `quote returns the golden present value and persists nothing`() {
        val dueDate = TermCalculator.referenceDate().plusMonths(3)
        assertEquals(0, receivableRepository.count())
        assertEquals(0, settlementRepository.count())

        mockMvc.perform(
            post("/api/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "faceValue": "100000.00",
                      "dueDate": "$dueDate",
                      "receivableType": "DUPLICATA_MERCANTIL",
                      "paymentCurrency": "BRL"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.termMonths").value(3))
            .andExpect(jsonPath("$.baseRate").value("0.01000000"))
            .andExpect(jsonPath("$.fxRateUsdBrl").value(nullValue()))
            .andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
            .andExpect(jsonPath("$.discountBrl").value("7140.06"))
            .andExpect(jsonPath("$.paymentAmount").value("92859.94"))
            .andExpect(jsonPath("$.paymentCurrency").value("BRL"))

        assertEquals(0, receivableRepository.count())
        assertEquals(0, settlementRepository.count())
    }

    @Test
    fun `settlement without idempotency key is rejected`() {
        mockMvc.perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"receivableId":"${UUID.randomUUID()}"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"))
    }

    @Test
    fun `settlement with a non uuid idempotency key is rejected`() {
        mockMvc.perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "not-a-uuid")
                .content("""{"receivableId":"${UUID.randomUUID()}"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Idempotency-Key must be a UUID"))
    }

    @Test
    fun `settlement with an empty body is rejected`() {
        mockMvc.perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request body is required"))
    }

    @Test
    fun `settlement of an unknown receivable is not found`() {
        val missingId = UUID.randomUUID()
        mockMvc.perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"receivableId":"$missingId"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Receivable $missingId not found"))
    }

    @Test
    fun `past due date is unprocessable and the body leaks neither stack nor sql`() {
        mockMvc.perform(
            post("/api/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "faceValue": "100000.00",
                      "dueDate": "2020-01-01",
                      "receivableType": "DUPLICATA_MERCANTIL",
                      "paymentCurrency": "BRL"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE"))
            .andExpect(jsonPath("$.message").value("dueDate must be after reference date"))
            .andExpect(content().string(not(containsString("at com."))))
            .andExpect(content().string(not(containsString("stackTrace"))))
            .andExpect(content().string(not(containsString("SELECT"))))
            .andExpect(content().string(not(containsString("INSERT"))))
            .andExpect(content().string(not(containsString("org.postgresql"))))
            .andExpect(content().string(not(containsString("org.hibernate"))))
    }
}
