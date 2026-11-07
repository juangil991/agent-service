# VidaCruz Agent (RF-04)

Servicio reactivo para RF-04: `POST /asistente/consulta` con recuperación híbrida (Voyage AI, pgvector y BM25 con RRF), respuesta con fuentes e integración LLM mediante **Spring AI**; y `GET /asistente/estado-urgencias`, una proyección eventual de los eventos de Triaje consumidos desde Kafka y persistidos en Redis.

## Requisitos

- JDK 21 o superior.
- API key de Google AI Studio para modo integrado:
  - `GOOGLE_AI_STUDIO_API_KEY`
- API key de Voyage AI para la consulta e ingesta RAG:
  - `VOYAGE_API_KEY`
- PostgreSQL separado para el agente, con pgvector y búsqueda BM25. Compose lo levanta con ParadeDB.

## Ejecutar

Para levantar Agent, Triaje y sus dependencias, ejecuta desde la raíz `VidaCruz`:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
docker compose run --rm agent --spring.main.web-application-type=none --app.kafka.consumer.enabled=false --app.assistant.ingestion.enabled=true
```

La API del Asistente queda en `http://localhost:8081`. Para ejecutar solo el Agent, desde `vidacruz-agent`:

```powershell
.\gradlew.bat bootRun
```

`bootRun` requiere que PostgreSQL del agente esté disponible (`AGENT_R2DBC_URL`, `AGENT_DB_USERNAME` y `AGENT_DB_PASSWORD`). En Compose, el agente usa una base separada del PostgreSQL de Triaje.

### Ejecutar solo el proyecto del agente con Docker

Desde `vidacruz-agent`, crea `.env` si aún no existe y configura `VOYAGE_API_KEY`. Después inicia los contenedores propios del agente:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
docker compose run --rm agent --spring.main.web-application-type=none --app.kafka.consumer.enabled=false --app.assistant.ingestion.enabled=true
```

La API queda en `http://localhost:8082` por defecto. Este Compose levanta solo el agente, ParadeDB (PostgreSQL con pgvector/BM25), Kafka con el tópico requerido y Redis; no inicia Triaje. El proveedor LLM por defecto es `stub`; puedes seleccionar Google AI Studio y configurar su API key desde `.env`. Los puertos de host (8082, 5434) pueden cambiarse en ese archivo.

### Ingesta offline de protocolos

Coloca los protocolos aprobados en Markdown en `vidacruz-agent/protocols` (o configura `PROTOCOLS_DIRECTORY`). Nombra cada archivo con su identificador, por ejemplo `PROTO-004.md`; usa un encabezado `#` para el título y encabezados Markdown para las secciones. El corpus inicial de tres protocolos incluido en el agente también se procesa.

La ingesta por encabezado/sección conserva la jerarquía (por ejemplo, `Criterios > Adultos`) y solo divide secciones largas en límites de párrafo/oración, hasta `PROTOCOL_CHUNK_MAX_CHARACTERS`. Genera embeddings de documento con el modelo Voyage AI configurado y hace upsert transaccional; los documentos cuyo hash no cambió no vuelven a generar embeddings.

Con Compose y `VOYAGE_API_KEY` definido en `.env`, ejecuta desde la raíz:

```powershell
docker compose up -d agent-postgres
docker compose run --build --rm agent --spring.main.web-application-type=none --app.kafka.consumer.enabled=false --app.assistant.ingestion.enabled=true
```

Para ejecución local desde `vidacruz-agent`, configura la URL/credenciales R2DBC y `VOYAGE_API_KEY`, inicia la base ParadeDB y ejecuta:

```powershell
.\gradlew.bat bootRun --args="--spring.main.web-application-type=none --app.kafka.consumer.enabled=false --app.assistant.ingestion.enabled=true"
```

La consulta genera un embedding de tipo `query`, combina los rankings BM25 y vectorial mediante RRF y solo conserva vecinos vectoriales dentro de `RAG_MAX_COSINE_DISTANCE`. Si no hay evidencia elegible, conserva la respuesta de abstención existente.

Cuando existe evidencia, el asistente evalúa en una segunda llamada cada afirmación factual o clínica de la respuesta contra los fragmentos recuperados. Si alguna no está respaldada, descarta toda la respuesta y devuelve la abstención sin fuentes; si no se puede completar o interpretar la verificación, devuelve `503` en vez de exponer contenido no verificado. La evaluación semántica con LLM reduce el riesgo de alucinaciones, pero no constituye una garantía matemática.

Endpoint:

- Compose: `POST http://localhost:8081/asistente/consulta`
- `GET http://localhost:8081/asistente/estado-urgencias` (con Compose)
- Standalone `bootRun`: `POST http://localhost:8080/asistente/consulta`

La respuesta del estado contiene `pacientesActivosPorNivel` con todos los niveles. `REGISTRADO` y `EN_CONSULTA` cuentan como activos; `ALTA` no. El estado se actualiza asíncronamente a partir de `triaje.eventos`, sin llamadas a la API de Triaje. La deduplicación de `eventId` y la actualización de paciente/conteos se ejecutan en una operación atómica de Redis.

Para ejecución independiente con `bootRun`, configura `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`/`REDIS_PORT` y el `KAFKA_TRIAGE_CONSUMER_GROUP`; el consumidor se puede deshabilitar con `KAFKA_CONSUMER_ENABLED=false`.

Payload (`Content-Type: application/json`):

```json
{
  "pregunta": "¿Cuándo se administra el primer antibiótico en sepsis?"
}
```

## Variables de entorno

- `ASSISTANT_LLM_PROVIDER` (`google-ai-studio`, `groqcloud`, `mistral`, `openai` o `stub`)
- `ASSISTANT_LANGUAGE` (default: `es`)
- `VOYAGE_API_KEY` (requerida para RAG); `VOYAGE_MODEL` (default: `voyage-4`, 1024 dimensiones)
- `AGENT_R2DBC_URL`, `AGENT_DB_USERNAME`, `AGENT_DB_PASSWORD` (conexión al almacén PostgreSQL del agente)
- `PROTOCOLS_DIRECTORY` (default: `./protocols`)
- `PROTOCOL_CHUNK_MAX_CHARACTERS` (default: `3000`)
- `RAG_CANDIDATE_LIMIT` (default: `20`), `RAG_RESULT_LIMIT` (default: `3`), `RAG_RRF_K` (default: `60`) y `RAG_MAX_COSINE_DISTANCE` (default: `0.55`)

Compatibilidad mantenida para Google AI Studio:

- `GOOGLE_AI_STUDIO_API_KEY` (requerida cuando `ASSISTANT_LLM_PROVIDER=google-ai-studio`)
- `GOOGLE_AI_STUDIO_MODEL` (default: `gemini-2.0-flash`)
- `GOOGLE_AI_STUDIO_BASE_URL` (default: `https://generativelanguage.googleapis.com/v1beta/openai`)
- `GOOGLE_AI_STUDIO_COMPLETIONS_PATH` (default: `/v1/chat/completions`)

Proveedor GroqCloud:

- `GROQCLOUD_API_KEY`
- `GROQCLOUD_MODEL` (default: `llama-3.1-70b-versatile`)
- `GROQCLOUD_BASE_URL` (default: `https://api.groq.com/openai`)
- `GROQCLOUD_COMPLETIONS_PATH` (default: `/v1/chat/completions`)

Proveedor Mistral:

- `MISTRAL_API_KEY`
- `MISTRAL_MODEL` (default: `mistral-large-latest`)
- `MISTRAL_BASE_URL` (default: `https://api.mistral.ai`)
- `MISTRAL_COMPLETIONS_PATH` (default: `/v1/chat/completions`)

Proveedor OpenAI:

- `OPENAI_API_KEY`
- `OPENAI_MODEL` (default: `gpt-4o-mini`)
- `OPENAI_BASE_URL` (default: `https://api.openai.com`)
- `OPENAI_COMPLETIONS_PATH` (default: `/v1/chat/completions`)

Si falta API key o el proveedor no está disponible, el endpoint devuelve `503 Service Unavailable`.
Cada consulta hace un solo intento al proveedor para que sus respuestas de indisponibilidad no queden demoradas por los reintentos internos de Spring AI; el cliente HTTP recibe el `503` explícito y puede reintentar después.
El circuit breaker del LLM se abre cuando fallan las últimas cinco llamadas, rechaza llamadas mientras está abierto y permite una llamada de prueba tras 30 segundos. Las consultas rechazadas mantienen el mismo contrato HTTP `503` (`mensaje` y `detalle`), sin devolver respuestas clínicas parciales.

Puedes ajustar la política con `LLM_CIRCUIT_BREAKER_FAILURE_WINDOW_SIZE` (default: `5`), `LLM_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD` (default: `100`), `LLM_CIRCUIT_BREAKER_OPEN_STATE_WAIT_DURATION` (default: `30s`) y `LLM_CIRCUIT_BREAKER_HALF_OPEN_CALLS` (default: `1`).

## Probar

```powershell
.\gradlew.bat test
```

