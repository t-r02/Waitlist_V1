package com.waitlist.ingestion.web;

import com.waitlist.ingestion.dto.SignupResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Honeypot interception: if the only (or one of the) failing fields is "website",
     * the submitter is a bot. Return a fake 200 duplicate response so the bot cannot
     * distinguish success from failure. Any remaining real field errors are still surfaced
     * as 400 when no honeypot is present.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors();

        boolean honeypotTriggered = fieldErrors.stream()
                .anyMatch(fe -> "website".equals(fe.getField()));

        if (honeypotTriggered) {
            // Silent 200 — bots cannot tell they were rejected
            return ResponseEntity.ok(new SignupResponse("Already registered", "00000000", true));
        }

        List<String> errors = fieldErrors.stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(body(400, "Validation failed", errors, null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(409, ex.getMessage(), null, null));
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(404, ex.getMessage(), null, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(409, "Request conflicts with existing data", null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        String correlationId = correlationId();
        log.error("Unexpected error [correlationId={}]", correlationId, ex);
        return ResponseEntity.internalServerError()
                .body(body(500, "An unexpected error occurred", null, correlationId));
    }

    private static Map<String, Object> body(int status, String message, List<String> errors, String correlationId) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", status);
        map.put("message", message);
        if (errors != null) map.put("errors", errors);
        if (correlationId != null) map.put("correlationId", correlationId);
        return map;
    }

    private static String correlationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
