package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", exception.getMessage()));
    }

    @ExceptionHandler(LlmUnavailableException.class)
    ResponseEntity<Map<String, String>> llmUnavailable(LlmUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "mensaje", "Servicio de asistente no disponible temporalmente",
                "detalle", exception.getMessage()));
    }
}

