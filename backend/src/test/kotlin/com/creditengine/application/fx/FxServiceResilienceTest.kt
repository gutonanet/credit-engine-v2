package com.creditengine.application.fx

import com.creditengine.application.settlement.SettleCommand
import com.creditengine.application.settlement.SettlementBodyHasher
import com.creditengine.application.settlement.SettlementService
import com.creditengine.domain.BusinessRuleException
import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableStatus
import com.creditengine.domain.model.ReceivableType
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Testcontainers
class FxServiceResilienceTest {
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

    @MockitoBean
    lateinit var fxService: FxService

    @Autowired lateinit var settlementService: SettlementService
    @Autowired lateinit var receivableRepository: ReceivableRepository
    @Autowired lateinit var settlementRepository: SettlementRepository

    @AfterEach
    fun clean() {
        settlementRepository.deleteAll()
        receivableRepository.deleteAll()
    }

    @Test
    fun `fx timeout aborts the settlement and rolls the transaction back`() {
        Mockito.`when`(fxService.getLatestRate("USD")).thenAnswer {
            Thread.sleep(3_000)
            BigDecimal("5.4321")
        }
        val receivable = receivableRepository.save(
            ReceivableEntity(
                assignorId = "cedente-fx",
                faceValue = BigDecimal("10000.00"),
                dueDate = LocalDate.now().plusMonths(3),
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                paymentCurrency = PaymentCurrency.USD,
            ),
        )
        val started = System.nanoTime()
        val error = assertThrows(BusinessRuleException::class.java) {
            settlementService.settle(
                SettleCommand(
                    receivableId = receivable.id,
                    idempotencyKey = UUID.randomUUID(),
                    requestBodyHash = SettlementBodyHasher.hash(receivable.id),
                ),
            )
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(error.message!!.contains("FX provider unavailable"))
        assertTrue(elapsedMs < 4_000, "timeout+retry took ${elapsedMs}ms")
        assertTrue(settlementRepository.findByReceivableId(receivable.id).isEmpty)
        assertEquals(ReceivableStatus.OPEN, receivableRepository.findById(receivable.id).get().status)
    }
}
