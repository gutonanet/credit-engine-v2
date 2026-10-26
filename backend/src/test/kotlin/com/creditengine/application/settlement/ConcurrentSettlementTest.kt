package com.creditengine.application.settlement

import com.creditengine.domain.model.PaymentCurrency
import com.creditengine.domain.model.ReceivableType
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.repository.ReceivableRepository
import com.creditengine.infrastructure.persistence.repository.SettlementRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentSettlementTest {
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
    @Autowired lateinit var writeGate: SettlementWriteGate

    @AfterEach
    fun resetGate() {
        writeGate.beforePersist = {}
        settlementRepository.deleteAll()
        receivableRepository.deleteAll()
    }

    @Test
    fun `two simultaneous settlements yield 201 and 409 from optimistic lock`() {
        val receivable = receivableRepository.save(
            ReceivableEntity(
                assignorId = "cedente-race",
                faceValue = BigDecimal("10000.00"),
                dueDate = LocalDate.now().plusMonths(3),
                receivableType = ReceivableType.DUPLICATA_MERCANTIL,
                paymentCurrency = PaymentCurrency.BRL,
            ),
        )
        val barrier = CyclicBarrier(2)
        writeGate.beforePersist = { barrier.await(15, TimeUnit.SECONDS) }

        val responses = (1..2).map {
            CompletableFuture.supplyAsync {
                mockMvc.perform(
                    post("/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""{"receivableId":"${receivable.id}"}"""),
                ).andReturn().response
            }
        }.map { it.get(30, TimeUnit.SECONDS) }

        val statuses = responses.map { it.status }.sorted()
        assertEquals(listOf(201, 409), statuses)
        val conflict = responses.first { it.status == 409 }
        assertTrue(conflict.contentAsString.contains("ObjectOptimisticLockingFailureException"))
        assertEquals(1, settlementRepository.count())
    }
}
