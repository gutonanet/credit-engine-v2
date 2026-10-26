package com.creditengine.domain

/** Regra de negócio recusada (SPEC: HTTP 422). Não é erro de formato do request. */
class BusinessRuleException(message: String) : RuntimeException(message)
