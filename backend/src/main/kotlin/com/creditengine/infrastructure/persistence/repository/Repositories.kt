package com.creditengine.infrastructure.persistence.repository

import com.creditengine.domain.model.RatePair
import com.creditengine.infrastructure.persistence.entity.RateVigencyEntity
import com.creditengine.infrastructure.persistence.entity.ReceivableEntity
import com.creditengine.infrastructure.persistence.entity.SettlementEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface RateVigencyRepository : JpaRepository<RateVigencyEntity, UUID> {
    fun findFirstByPairAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
        pair: RatePair,
        at: Instant,
    ): RateVigencyEntity?
}

interface ReceivableRepository : JpaRepository<ReceivableEntity, UUID>

interface SettlementRepository : JpaRepository<SettlementEntity, UUID> {
    fun findByIdempotencyKey(idempotencyKey: UUID): Optional<SettlementEntity>

    fun findByReceivableId(receivableId: UUID): Optional<SettlementEntity>
}
