package com.patorinaldi.wallet.transaction.exception.handler;

import com.patorinaldi.wallet.transaction.dto.ErrorResponse;
import com.patorinaldi.wallet.transaction.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex,
            HttpServletRequest request) {

        log.warn("Insufficient balance for {} {}: walletId={}, requested={}, available={}",
                request.getMethod(), request.getRequestURI(),
                ex.getWalletId(), ex.getRequestedAmount(), ex.getCurrentBalance());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                400,
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({WalletBalanceNotFoundException.class, TransactionNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException ex,
            HttpServletRequest request) {

        log.warn("Resource not found for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                404,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTransaction(
            DuplicateTransactionException ex,
            HttpServletRequest request) {

        log.warn("Duplicate transaction for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                409,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(UserBlockedException.class)
    public ResponseEntity<ErrorResponse> handleUserBlocked(
            UserBlockedException ex,
            HttpServletRequest request) {

        log.warn("Blocked user transaction attempt for {} {}: userId={}, reason={}",
                request.getMethod(), request.getRequestURI(), ex.getUserId(), ex.getReason());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                403,
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error for {} {}: {}", request.getMethod(), request.getRequestURI(), errorMessage);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                400,
                "Bad Request",
                errorMessage,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                500,
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}