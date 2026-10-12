# Aura Skincare Voice CX Agent Backend

## Run locally

Install JDK 21 and Maven, then set the Gemini Developer API key and Sarvam subscription key in PowerShell. API keys are read only from the environment and must not be committed.

```powershell
$env:GEMINI_API_KEY = "your_api_key"
$env:GEMINI_MODEL = "gemini-3.5-flash-lite"
$env:SARVAM_API_KEY = "your_sarvam_api_key"
mvn spring-boot:run
```

Alternatively copy `.env.example` as a reference for the variable names; Spring does not load `.env` automatically. Set those values in the process environment before launching the app. Sarvam HTTP timeouts can be adjusted with `SARVAM_CONNECT_TIMEOUT` and `SARVAM_READ_TIMEOUT`.

## Render configuration

In the Render service dashboard, add `GEMINI_API_KEY` and `SARVAM_API_KEY` as secret environment variables. Set `FRONTEND_ORIGIN` to the deployed frontend's exact origin (default: `https://aura-skin-care-frontend.vercel.app`). Add `GEMINI_MODEL` if you want to override the configured default (`gemini-3.5-flash-lite`). Optional tuning variables are `GEMINI_CONNECT_TIMEOUT`, `GEMINI_READ_TIMEOUT`, `GEMINI_MAX_TOOL_ITERATIONS`, `SARVAM_CONNECT_TIMEOUT`, and `SARVAM_READ_TIMEOUT`. Do not place a real key in source control or build logs.

## Chat endpoint

`POST /api/calls/chat`

Request:

```json
{
  "sessionId": "conversation-123",
  "message": "What are your return rules?",
  "turnId": "optional-client-turn-id"
}
```

Successful response (`200 OK`):

```json
{
  "reply": "Returns are eligible within 7 days after delivery when the product is unopened, unused, and in its original packaging.",
  "turnId": "optional-client-turn-id"
}
```

The backend uses Gemini's `generateContent` REST API with native function calling. Order lookups require backend verification. Cancellation can only be authorized after confirmation and revalidation; cancellation execution is not implemented, so the endpoint must not be interpreted as cancelling an order.

## Speech endpoint

`POST /api/calls/speak` accepts `{"text":"Hello!"}` and returns the first decoded Sarvam WAV audio item as `audio/wav`. It uses Sarvam Bulbul v4 Flash with the `simran_en_customer` English voice. The text limit is 2,500 Unicode characters. The Sarvam subscription key must be supplied through `SARVAM_API_KEY`; it is never returned to the frontend.
