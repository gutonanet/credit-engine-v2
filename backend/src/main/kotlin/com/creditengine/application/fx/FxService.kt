package com.creditengine.application.fx

import com.creditengine.application.rate.RateVigencyService
import com.creditengine.domain.BusinessRuleException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Supplier

interface FxService {
    fun getLatestRate(pair: String): BigDecimal
}

/** Provedor mockado: a vigência USD_BRL persistida faz o papel da tesouraria. */
@Service
class VigencyFxService(
    private val rateVigencyService: RateVigencyService,
) : FxService {
    override fun getLatestRate(pair: String): BigDecimal {
        require(pair == "USD") { "unsupported FX pair: $pair" }
        return rateVigencyService.effectiveUsdBrl(Instant.now())
    }
}

/**
 * Cada tentativa do provedor tem timeout de 1s, no máximo 2 tentativas, e o circuito abre
 * se as falhas se acumulam. O fallback não reaproveita taxa antiga: a liquidação recebe 422.
 */
@Service
class ResilientFxService(
    private val fxService: FxService,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    retryRegistry: RetryRegistry,
    timeLimiterRegistry: TimeLimiterRegistry,
) {
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fx-quote").apply { isDaemon = true }
    }
    private val timeLimiter = timeLimiterRegistry.timeLimiter("fx")
    private val attempt: Supplier<BigDecimal> = Retry.decorateSupplier(
        retryRegistry.retry("fx"),
        CircuitBreaker.decorateSupplier(
            circuitBreakerRegistry.circuitBreaker("fx"),
            Supplier {
                timeLimiter.executeFutureSupplier {
                    CompletableFuture.supplyAsync({ fxService.getLatestRate("USD") }, executor)
                }
            },
        ),
    )

    fun getLatestRate(pair: String): BigDecimal {
        require(pair == "USD") { "unsupported FX pair: $pair" }
        try {
            return attempt.get()
        } catch (ex: BusinessRuleException) {
            throw ex
        } catch (ex: Exception) {
            throw BusinessRuleException("FX provider unavailable: ${ex.javaClass.simpleName}")
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}
