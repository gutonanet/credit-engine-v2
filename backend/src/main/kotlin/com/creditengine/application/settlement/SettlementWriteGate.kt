package com.creditengine.application.settlement

import org.springframework.stereotype.Component

/**
 * Ponto de sincronização vazio em produção. O teste de concorrência segura as duas
 * transações depois da leitura da versão e antes do update, para forçar o conflito de @Version.
 */
@Component
class SettlementWriteGate {
    @Volatile
    var beforePersist: () -> Unit = {}

    fun hold() = beforePersist()
}
