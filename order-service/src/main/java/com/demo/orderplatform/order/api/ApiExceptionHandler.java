package com.demo.orderplatform.order.api;

import com.demo.orderplatform.order.service.CatalogExceptions;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "detail", detail));
    }

    @ExceptionHandler(CatalogExceptions.UnknownSku.class)
    public ResponseEntity<Map<String, Object>> onUnknownSku(CatalogExceptions.UnknownSku e) {
        return ResponseEntity.badRequest().body(Map.of("error", "unknown_sku", "detail", e.getMessage()));
    }

    @ExceptionHandler(CatalogExceptions.CatalogUnavailable.class)
    public ResponseEntity<Map<String, Object>> onCatalogDown(CatalogExceptions.CatalogUnavailable e) {
        log.warn("catalog unavailable", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "catalog_unavailable", "detail", e.getMessage()));
    }
}
