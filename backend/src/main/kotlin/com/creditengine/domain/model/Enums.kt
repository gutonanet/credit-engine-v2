package com.creditengine.domain.model

enum class ReceivableType {
    DUPLICATA_MERCANTIL,
    CHEQUE_PREDATADO,
}

enum class PaymentCurrency {
    BRL,
    USD,
}

enum class ReceivableStatus {
    OPEN,
    SETTLED,
}

enum class RatePair {
    BASE_RATE,
    USD_BRL,
}
