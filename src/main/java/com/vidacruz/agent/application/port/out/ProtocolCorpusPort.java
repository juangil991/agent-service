package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.ProtocolChunk;

import java.util.List;

public interface ProtocolCorpusPort {

    List<ProtocolChunk> allChunks();
}

