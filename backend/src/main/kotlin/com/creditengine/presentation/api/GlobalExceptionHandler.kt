package com.creditengine.presentation.api

import com.creditengine.application.rate.MissingRateVigencyException
import com.creditengine.domain.BusinessRuleException
import com.creditengine.application.receivable.ReceivableNotFoundException
import com.creditengine.application.settlement.ConcurrentSettlementException
import com.creditengine.application.settlement.IdempotencyConflictException
import com.creditengine.application.settlement.ReceivableAlreadySettledException
import com.creditengine.presentation.dto.ErrorResponse
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.slf4j.LoggerFactory
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldError = ex.bindingResult.fieldErrors.firstOrNull()
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                code = "VALIDATION_ERROR",
                message = fieldError?.defaultMessage ?: "Invalid request",
                field = fieldError?.field,
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "VALIDATION_ERROR", message = ex.message ?: "Invalid request"),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "VALIDATION_ERROR", message = "Request body is required"),
        )

    @ExceptionHandler(ReceivableNotFoundException::class)
    fun notFound(ex: ReceivableNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(code = "NOT_FOUND", message = ex.message ?: "Not found"),
        )

    @ExceptionHandler(MissingRateVigencyException::class, BusinessRuleException::class)
    fun unprocessable(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(code = "BUSINESS_RULE", message = ex.message ?: "Business rule rejected"),
        )

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                code = ex.javaClass.simpleName,
                message = "Concurrent settlement conflict",
            ),
        )

    @ExceptionHandler(
        ReceivableAlreadySettledException::class,
        IdempotencyConflictException::class,
        ConcurrentSettlementException::class,
    )
    fun conflict(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = "CONFLICT", message = ex.message ?: "Conflict"),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "VALIDATION_ERROR", message = "Invalid parameter: ${ex.name}"),
        )

    @ExceptionHandler(Exception::class)
    fun generic(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(code = "INTERNAL_ERROR", message = "Unexpected error"),
        )
    }
}
