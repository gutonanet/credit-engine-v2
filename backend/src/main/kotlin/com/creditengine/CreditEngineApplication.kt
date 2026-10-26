package com.creditengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CreditEngineApplication

fun main(args: Array<String>) {
    runApplication<CreditEngineApplication>(*args)
}
