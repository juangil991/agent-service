package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/asistente")
public class AssistantController {

    private final AskAssistantUseCase askAssistant;

    public AssistantController(AskAssistantUseCase askAssistant) {
        this.askAssistant = askAssistant;
    }

    @PostMapping(path = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<AssistantQueryResponse> consulta(@RequestBody AssistantQueryRequest request) {
        if (request == null || request.pregunta() == null || request.pregunta().isBlank()) {
            return Mono.error(new IllegalArgumentException("pregunta es obligatoria"));
        }
        return askAssistant.ask(request.pregunta()).map(AssistantQueryResponse::from);
    }
}

