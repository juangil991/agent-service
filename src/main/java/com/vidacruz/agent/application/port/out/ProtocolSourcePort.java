package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.ProtocolDocument;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProtocolSourcePort {

    Mono<List<ProtocolDocument>> loadDocuments();
}
