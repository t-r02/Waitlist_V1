package com.waitlist.admin.exception;

import com.waitlist.admin.filter.CorrelationIdFilter;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for the admin-service {@link GlobalExceptionHandler}.
 *
 * <p>Note: unlike ingestion-service, this handler does NOT map IllegalArgumentException → 400
 * (it falls through to the 500 handler). This test verifies that intended difference.
 */
class AdminGlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/illegal-state")
        void illegalState() { throw new IllegalStateException("state conflict"); }

        @GetMapping("/test/not-found")
        void notFound() { throw new NoSuchElementException("item not found"); }

        @GetMapping("/test/entity-not-found")
        void entityNotFound() { throw new EntityNotFoundException("entity gone"); }

        @GetMapping("/test/type-mismatch")
        void typeMismatch() {
            throw new TypeMismatchException("BOGUS", Status.class);
        }

        @GetMapping("/test/unexpected")
        void unexpected() { throw new RuntimeException("unexpected boom"); }

        @GetMapping("/test/illegal-arg")
        void illegalArg() { throw new IllegalArgumentException("not handled by admin handler"); }
    }

    // Stand-in Status class for testing TypeMismatchException
    static class Status {}

    @Test
    void illegalState_returns409() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("state conflict"));
    }

    @Test
    void noSuchElement_returns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void entityNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("BOGUS")));
    }

    @Test
    void unexpectedException_returns500WithCorrelationId() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void illegalArgument_fallsThrough_returns500() throws Exception {
        // Admin handler has no @ExceptionHandler(IllegalArgumentException) —
        // it deliberately falls through to handleUnexpected → 500
        mockMvc.perform(get("/test/illegal-arg"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
