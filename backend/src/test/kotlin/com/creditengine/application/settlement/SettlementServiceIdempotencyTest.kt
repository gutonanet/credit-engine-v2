package com.creditengine.application.settlement

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Testcontainers
@SpringBootTest
class SettlementServiceIdempotencyTest {
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

    @Autowired lateinit var settlementService: SettlementService
    @Autowired lateinit var receivableRepository: ReceivableRepository
    @Autowired lateinit var settlementRepository: SettlementRepository

    @BeforeEach
    fun cleanDatabase() {
        settlementRepository.deleteAll()
        receivableRepository.deleteAll()
    }

    @Test
    fun `same idempotency key replays without duplicate settlement`() {
        val receivable = receivableRepository.save(
            ReceivableEntity(
                assignorId = "cedente-a",
                faceValue = BigDecimal("10000.00"),
                dueDate = LocalDate.now().plusMonths(3),
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                paymentCurrency = PaymentCurrency.BRL,
            ),
        )
        val key = UUID.randomUUID()
        val hash = SettlementBodyHasher.hash(receivable.id)
        val command = SettleCommand(receivable.id, key, hash)

        val first = settlementService.settle(command)
        val second = settlementService.settle(command)

        assertTrue(!first.replay)
        assertTrue(second.replay)
        assertEquals(first.settlement.id, second.settlement.id)
        assertEquals(1, settlementRepository.count())
    }

    @Test
    fun `same key with different body hash conflicts`() {
        val receivable = receivableRepository.save(
            ReceivableEntity(
                assignorId = "cedente-b",
                faceValue = BigDecimal("5000.00"),
                dueDate = LocalDate.now().plusMonths(2),
                receivableType = ReceivableType.CHEQUE_PREDATADO,
                paymentCurrency = PaymentCurrency.BRL,
            ),
        )
        val key = UUID.randomUUID()
        settlementService.settle(
            SettleCommand(receivable.id, key, SettlementBodyHasher.hash(receivable.id)),
        )

        assertThrows(IdempotencyConflictException::class.java) {
            settlementService.settle(
                SettleCommand(receivable.id, key, "deadbeef"),
            )
        }
    }
}
