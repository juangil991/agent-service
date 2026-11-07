package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.ProtocolChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProtocolRetrievalPort {

    Mono<List<ProtocolChunk>> retrieve(String question, int limit);
}
