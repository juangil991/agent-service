package com.vidacruz.agent.infrastructure.adapter.out.corpus;

import com.vidacruz.agent.application.port.out.ProtocolSourcePort;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class InMemoryProtocolCorpusAdapter implements ProtocolSourcePort {

    private static final List<ProtocolDocument> DOCUMENTS = List.of(
            new ProtocolDocument(
                    "seed:PROTO-001",
                    "PROTO-001",
                    "Crisis hipertensiva",
                    "1",
                    "seed",
                    """
                            # Crisis hipertensiva

                            ## Definición
                            Crisis hipertensiva: presión arterial mayor o igual a 180/120 mmHg. Si hay daño de órgano diana es emergencia; si no, urgencia.

                            ## Medidas iniciales
                            Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25% en la primera hora. Vigilar signos de encefalopatía.

                            ## Contraindicaciones
                            Nifedipino sublingual y reducción intravenosa rápida sin monitorización en cuidados intensivos están contraindicadas.
                            """),
            new ProtocolDocument(
                    "seed:PROTO-002",
                    "PROTO-002",
                    "Sepsis",
                    "1",
                    "seed",
                    """
                            # Sepsis

                            ## Criterios
                            Sepsis con qSOFA: dos o más de frecuencia respiratoria mayor o igual a 22, alteración del estado mental, presión sistólica menor o igual a 100 mmHg.

                            ## Acciones iniciales
                            En sepsis, tomar lactato y hemocultivos antes del antibiótico. Administrar antibiótico de amplio espectro en la primera hora. Dar 30 ml/kg de cristaloides si hay hipotensión o lactato mayor o igual a 4.
                            """),
            new ProtocolDocument(
                    "seed:PROTO-003",
                    "PROTO-003",
                    "Deshidratación pediátrica",
                    "1",
                    "seed",
                    """
                            # Deshidratación pediátrica

                            ## Clasificación
                            Deshidratación pediátrica: leve 3-5% de pérdida de peso, moderada 5-8%, severa mayor al 8% o alteración del estado mental.

                            ## Rehidratación
                            Leve o moderada sin vómito: solución oral 50 ml/kg en 4 horas. Severa: vía intravenosa con bolo de 20 ml/kg.
                            """)
    );

    @Override
    public Mono<List<ProtocolDocument>> loadDocuments() {
        return Mono.just(DOCUMENTS);
    }
}

