# VidaCruz Agent (RF-04A)

Servicio reactivo para la Parte A de RF-04: `POST /asistente/consulta` con recuperación sobre corpus clínico, respuesta con fuentes e integración LLM mediante **Spring AI**.

## Requisitos

- JDK 17 o superior.
- API key de Google AI Studio para modo integrado:
  - `GOOGLE_AI_STUDIO_API_KEY`

## Ejecutar

Desde `vidacruz-agent`:

```powershell
.\gradlew.bat bootRun
```

Endpoint:

- `POST http://localhost:8080/asistente/consulta`

Payload (`Content-Type: application/json`):

```json
{
  "pregunta": "¿Cuándo se administra el primer antibiótico en sepsis?"
}
```

## Variables de entorno

- `ASSISTANT_LLM_PROVIDER` (`google-ai-studio`, `groqcloud`, `mistral`, `openai` o `stub`)
- `ASSISTANT_LANGUAGE` (default: `es`)

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

## Probar

```powershell
.\gradlew.bat test
```

