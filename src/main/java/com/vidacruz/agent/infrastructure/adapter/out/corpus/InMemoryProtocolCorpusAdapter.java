package com.vidacruz.agent.infrastructure.adapter.out.corpus;

import com.vidacruz.agent.application.port.out.ProtocolCorpusPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InMemoryProtocolCorpusAdapter implements ProtocolCorpusPort {

    private static final List<ProtocolChunk> CHUNKS = List.of(
            new ProtocolChunk(
                    "PROTO-001",
                    "Definición",
                    "Crisis hipertensiva: presión arterial mayor o igual a 180/120 mmHg. "
                            + "Si hay daño de órgano diana es emergencia; si no, urgencia."),
            new ProtocolChunk(
                    "PROTO-001",
                    "Medidas iniciales",
                    "Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión "
                            + "más de un 25% en la primera hora. Vigilar signos de encefalopatía."),
            new ProtocolChunk(
                    "PROTO-001",
                    "Contraindicaciones",
                    "Nifedipino sublingual y reducción intravenosa rápida sin monitorización "
                            + "en cuidados intensivos están contraindicadas."),
            new ProtocolChunk(
                    "PROTO-002",
                    "Criterios",
                    "Sepsis con qSOFA: dos o más de frecuencia respiratoria mayor o igual a 22, "
                            + "alteración del estado mental, presión sistólica menor o igual a 100 mmHg."),
            new ProtocolChunk(
                    "PROTO-002",
                    "Acciones iniciales",
                    "En sepsis, tomar lactato y hemocultivos antes del antibiótico. Administrar antibiótico "
                            + "de amplio espectro en la primera hora. Dar 30 ml/kg de cristaloides "
                            + "si hay hipotensión o lactato mayor o igual a 4."),
            new ProtocolChunk(
                    "PROTO-003",
                    "Clasificación",
                    "Deshidratación pediátrica: leve 3-5% de pérdida de peso, moderada 5-8%, "
                            + "severa mayor al 8% o alteración del estado mental."),
            new ProtocolChunk(
                    "PROTO-003",
                    "Rehidratación",
                    "Leve o moderada sin vómito: solución oral 50 ml/kg en 4 horas. "
                            + "Severa: vía intravenosa con bolo de 20 ml/kg.")
    );

    @Override
    public List<ProtocolChunk> allChunks() {
        return CHUNKS;
    }
}

