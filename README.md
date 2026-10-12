# Aura Skincare — AI Voice Customer Experience Agent

A browser-based AI voice customer support agent for Aura Skincare, a fictional Indian skincare brand. The application combines conversational AI, backend tool calling, mock order data, policy-aware responses, speech generation, and post-call summaries.

**Live application:** https://aura-skin-care-frontend.vercel.app  
**Backend API:** https://aura-voice-cx-agent.onrender.com  
**Backend health check:** https://aura-voice-cx-agent.onrender.com/health  
**GitHub repository:** https://github.com/ganesh-hirve/aura-voice-cx-agent  
**Developer:** https://www.linkedin.com/in/ganesh-hirve-952609298

---

## 1. Problem Statement

Customer support for an e-commerce brand involves answering product and policy questions, retrieving order information, explaining eligibility for actions, and communicating clearly when information is unavailable.

The goal of this project is to build a browser-based AI voice agent that can:

- Interact with customers through a conversational interface.
- Answer questions using the available brand policies.
- Retrieve order details through backend tools.
- Apply deterministic business rules to policy-sensitive requests.
- Avoid inventing order details or unsupported policy exceptions.
- Provide spoken responses with a speech-synthesis fallback.
- Produce a chronological transcript and structured post-call summary.

The system uses a fictional brand and seeded order records to make its behaviour reproducible during evaluation.

## 2. Architecture Overview

The application follows a frontend–backend architecture. The frontend manages the customer experience, the backend coordinates provider calls and owns business logic, Gemini handles natural-language reasoning, and H2 stores mock order data.

```text
                         CUSTOMER
                            |
                            v
                 React + TypeScript UI
                            |
                 Browser audio interaction
                            |
                            v
                    Spring Boot API
                            |
              +-------------+-------------+
              |                           |
              v                           v
         Gemini API                  H2 Database
              |                           ^
              v                           |
      Native Function Calling             |
              |                           |
              v                           |
       Backend Tool Services --------------+
              |
              +---- Order lookup
              |
              +---- Policy eligibility
              |
              +---- Action prerequisites
              |
              v
       Final conversational response
              |
              v
       Frontend transcript and UI
              |
              +---- Sarvam TTS
              |
              +---- Browser speech fallback
              |
              v
          Audio playback
              |
              v
       Transcript and call summary
```

### Request lifecycle

For a customer asking, "Where is my order 101?":

1. The frontend captures the customer's input through the implemented input path.
2. The frontend sends the message to the Spring Boot chat endpoint.
3. Spring Boot sends the conversation request to Gemini.
4. Gemini interprets the request and can request a structured order-details tool call.
5. The backend validates the tool arguments and retrieves the corresponding order.
6. The order details are returned to Gemini for conversational response generation.
7. The frontend displays the response and updates the conversation transcript.
8. The application attempts server-side speech generation and can use browser speech synthesis when necessary.
9. At the end of the call, the application presents the available transcript and structured summary.

The backend, rather than the model, is responsible for authoritative order data and policy-sensitive decisions.

## 3. Technology Stack and Rationale

| Component | Technology | Why it was selected |
|---|---|---|
| Frontend | React | Component-based UI and manageable interaction states |
| Frontend language | TypeScript | Type checking for UI state and API data |
| Backend | Java 21 | Strong typing and a mature backend ecosystem |
| Backend framework | Spring Boot | REST APIs, dependency injection, configuration and testing |
| Conversational AI | Gemini API | Natural-language understanding and response generation |
| Tool integration | Gemini native function calling | Structured requests for backend operations |
| Database | H2 | Lightweight relational storage for seeded assessment data |
| Text-to-speech | Sarvam Bulbul v4 Flash | Dedicated server-side speech-generation integration |
| Speech fallback | Browser Speech Synthesis | Basic voice output when server TTS is unavailable |
| Backend tests | JUnit and Spring Boot testing | Verification of backend behaviour |
| Frontend tests | Existing frontend test suite | Verification of frontend behaviour |
| Frontend hosting | Vercel | Independent frontend deployment |
| Backend hosting | Render | Independent Java backend deployment |

### Why React and TypeScript?

React supports reusable UI components and explicit states such as listening, thinking, speaking, and completed-call states. TypeScript helps catch mismatches between expected API responses and frontend data structures.

A voice application has more interaction states than a conventional static page. A component-based interface makes those states easier to manage and test.

### Why Java 21 and Spring Boot?

Spring Boot provides a structured backend for handling REST requests, coordinating external APIs, retrieving order data, and enforcing business rules.

Keeping this logic in the backend prevents the frontend or the LLM from becoming the authority for order status or action eligibility.

### Why Gemini?

Customer requests are expressed in natural language and may contain incomplete or ambiguous information. Gemini interprets intent and generates conversational responses while requesting backend tools when appropriate.

Gemini is responsible for understanding the request, not independently authorizing business actions.

### Why native function calling?

Function calling creates a structured interface between the model and application capabilities. Instead of relying on the model to guess an order status, the backend can retrieve the actual seeded record.

Function calling alone is not a security mechanism. The backend must still validate arguments, check the requested operation, and enforce its rules.

### Why H2?

H2 keeps local setup simple and supports structured, repeatable mock-order queries. It is appropriate for a small assessment dataset.

For production, I would evaluate a managed relational database such as PostgreSQL or MySQL, with explicit migrations, backups, transaction management, and persistent storage configuration.

### Why Sarvam and browser speech synthesis?

Sarvam provides a dedicated server-side TTS integration. Browser speech synthesis provides a fallback path when the primary speech service is unavailable.

The fallback improves degraded-mode usability, but browser voices and pronunciation vary across operating systems and browsers. The Sarvam path should only be considered fully operational after successful end-to-end verification against the deployed application.

### Why separate frontend and backend deployments?

The frontend and backend have different runtime responsibilities. Independent deployment allows them to be configured and updated separately.

The trade-off is that production environment variables, public API access, HTTPS and CORS must all be configured correctly.

---

## 4. Brand Knowledge and Policy Rules

The agent uses the following fictional Aura Skincare policies.

| Topic | Policy |
|---|---|
| Shipping | Free shipping on orders above ₹499; otherwise ₹50 |
| Delivery | Estimated delivery in 3–5 business days |
| Returns | Within 7 days of delivery for products that are unopened, unused and in original packaging |
| Damaged or defective items | Report within 48 hours with photos to request a replacement |
| Cancellation | Allowed only while an order is Processing |
| Shipped orders | Cannot be cancelled once Shipped or Out for Delivery; the customer may refuse delivery at the doorstep |
| Cash on Delivery | Available up to ₹2,500; payment by cash or UPI at the doorstep |

The agent should not invent missing product information, order details, tracking updates, or policy exceptions. When the available information is insufficient, it should acknowledge the limitation instead of guessing.

These rules describe the fictional assessment brand and should not be interpreted as policies of a real company.

## 5. Mock Order Dataset

The database contains three seeded demonstration orders.

| Order ID | Customer | Product | Amount | Status |
|---|---|---|---:|---|
| ORD-101 | Priya Sharma | Vitamin C Serum 30ml | ₹699 | Out for Delivery |
| ORD-102 | Rahul Verma | Hydrating Sunscreen SPF 50 | ₹499 | Delivered |
| ORD-103 | Ananya Patel | Green Tea Face Wash + Toner | ₹850 | Processing |

### Additional order information

**ORD-101**
- Courier: BlueDart
- Tracking reference: `BD-982103`
- Expected delivery: 6 PM today, according to the seeded demonstration data

**ORD-102**
- Courier: Delhivery
- Tracking reference: `DL-441029`
- Delivery recorded as 14 days ago

**ORD-103**
- Order placed 3 hours ago, according to the seeded demonstration data
- Status: Processing
- Eligible for cancellation under the stated mock policy, subject to any required verification and confirmation

These records are static assessment data. They are not live courier tracking results or real customer orders.

## 6. API Documentation

### 6.1 Health check

**Endpoint:** `GET /health`

Checks whether the backend is responding.

Example response:

```json
{
  "status": "UP"
}
```

### 6.2 Chat endpoint

**Endpoint:** `POST /api/calls/chat`

Accepts a customer message and returns a conversational response.

Example request:

```json
{
  "sessionId": "conversation-123",
  "message": "What are your return rules?",
  "turnId": "optional-client-turn-id"
}
```

Example response:

```json
{
  "reply": "Returns are eligible within 7 days after delivery when the product is unopened, unused, and in its original packaging.",
  "turnId": "optional-client-turn-id"
}
```

The exact response fields depend on the implemented endpoint contract. The example illustrates the expected shape.

The backend integrates with Gemini's `generateContent` REST API and native function calling. The model can request an order lookup, but the backend remains responsible for retrieving order data and applying deterministic checks.

#### Important cancellation limitation

Cancellation eligibility and confirmation can be discussed by the application, but cancellation execution is not implemented in the current backend. A confirmation must not be reported as a completed cancellation, and no order state change should be implied.

### 6.3 Text-to-speech endpoint

**Endpoint:** `POST /api/calls/speak`

Accepts text and returns decoded Sarvam audio as `audio/wav`.

Example request:

```json
{
  "text": "Hello! Welcome to Aura Skincare."
}
```

The endpoint uses Sarvam Bulbul v4 Flash and the configured `simran_en_customer` English voice. The text limit is 2,500 Unicode characters.

The Sarvam API key is supplied through the backend environment and must never be exposed to the frontend.

If server-side TTS fails, the frontend can use browser speech synthesis as a fallback, provided that browser speech synthesis is available.

---

## 7. Run Locally

### Prerequisites

- JDK 21
- Maven
- Node.js and npm
- Gemini API key
- Sarvam API key for server-side TTS

### 7.1 Backend setup

Open PowerShell in the backend repository.

Set the required environment variables:

```powershell
$env:GEMINI_API_KEY = "your_gemini_api_key"
$env:GEMINI_MODEL = "gemini-3.5-flash-lite"
$env:SARVAM_API_KEY = "your_sarvam_api_key"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
mvn spring-boot:run
```

Use the model configured and supported by the actual deployed application. The value above should match the project's current configuration.

Verify the backend using:

`http://localhost:8080/health`

The port may differ if the application has been configured to use another port.

### 7.2 Frontend setup

Open a second terminal in the frontend repository, install dependencies and start the development server:

```powershell
npm install
npm run dev
```

Configure the frontend API base URL to point to the local backend.

For the deployed application, the backend base URL is:

`https://aura-voice-cx-agent.onrender.com`

Follow the frontend repository's actual environment-variable naming convention when configuring the API URL.

### 7.3 Environment variables

The backend `.env.example` documents expected environment variable names. Spring Boot does not automatically load a plain `.env` file, so values must be supplied through the process environment or a suitable local environment loader.

Never commit real API keys.

---

## 8. Deployment

### 8.1 Backend on Render

Configure the following environment variables in the Render dashboard.

**Required**
- `GEMINI_API_KEY`
- `SARVAM_API_KEY`

**Recommended**
- `FRONTEND_ORIGIN=https://aura-skin-care-frontend.vercel.app`
- `GEMINI_MODEL` set to the model configured in the backend

**Optional tuning variables**
- `GEMINI_CONNECT_TIMEOUT`
- `GEMINI_READ_TIMEOUT`
- `GEMINI_MAX_TOOL_ITERATIONS`
- `SARVAM_CONNECT_TIMEOUT`
- `SARVAM_READ_TIMEOUT`

Use the exact frontend origin in the CORS configuration. Provider credentials should be stored as secret environment variables rather than in source files or build logs.

### 8.2 Frontend on Vercel

Configure the frontend API base URL to point to the Render backend. Make sure production environment variables are set for the correct Vercel environment and redeploy if a variable changes.

Verify public access using a fresh browser session. A successful local development test does not prove that the public deployment is correctly configured.

### 8.3 Deployment verification

Verify these independently:

1. The frontend URL loads publicly.
2. The backend health endpoint responds.
3. Chat requests reach the deployed backend.
4. CORS permits the intended frontend origin.
5. Order lookups return the expected seeded records.
6. Transcript and summary behaviour works after a call.
7. Server-side TTS works, or the browser fallback provides usable audio.

Do not mark a provider integration as working solely because the backend has started successfully.

---

## 9. Business Logic and Safety

### Why the LLM is not the source of truth

LLMs generate text probabilistically. They can misunderstand a request or produce a plausible but incorrect answer.

The backend therefore owns:
- Order retrieval
- Order-status data
- Policy eligibility
- Tool argument validation
- Verification and action prerequisites
- State-changing operations, when implemented

Gemini handles intent interpretation and conversational wording. This separation reduces the risk of unsupported claims and unauthorized operations.

### Unknown orders

An unknown order ID should return an explicit not-found result. The system must not silently map an invalid ID to a different seeded order.

### Missing information

If the available brand knowledge or order record does not answer a question, the agent should say that the information is unavailable rather than inventing a product ingredient, tracking update or policy exception.

### Cancellation safety

The current version documents cancellation eligibility but does not execute cancellation. A production implementation should include:
- Customer and order authorization
- Revalidation of the current order status
- Explicit customer confirmation
- A transactional state change
- Idempotency protection against duplicate requests
- An auditable success or failure result

An LLM response or browser-side confirmation alone is not sufficient authorization for a business action.

### Stale conversational turns

If a newer request supersedes an older one, the system should prevent an outdated turn from performing an action. A frontend request cancellation is not equivalent to rolling back a server-side operation. Any state-changing operation needs server-side validation and appropriate concurrency protection.

---

## 10. Testing Strategy

The project should be evaluated at both the unit-test and deployed-application levels.

### Order and policy cases

- Valid order IDs
- Missing order IDs
- Unknown order IDs
- Correct retrieval of each seeded order
- Shipping-fee boundaries
- Return eligibility inside and outside the seven-day window
- Damaged-item reporting inside and outside the 48-hour window
- Cancellation eligibility for Processing versus Shipped or Out for Delivery orders
- Explicit handling of unsupported actions
- Questions for which product information is unavailable

### Voice and UI cases

- Microphone permission granted
- Microphone permission denied
- Listening, thinking and speaking states
- Transcript order and content
- Structured post-call summary
- Server-side TTS success
- Server-side TTS failure
- Browser speech-synthesis fallback
- Fresh-browser access

### Backend and deployment cases

- Gemini provider failure
- Missing or invalid API credentials
- CORS between the deployed frontend and backend
- Backend cold start
- API timeout handling
- Invalid tool arguments
- Stale-turn protection for any state-changing operation

Automated test results and public deployment verification are different forms of evidence. A passing unit test does not establish that an external provider or production browser workflow is working correctly.

---

## 11. Engineering Decisions: Questions and Answers

### Q1. Why did you choose this architecture?

The application separates the customer-facing interface from backend services and LLM reasoning. React and TypeScript manage the interaction, Spring Boot owns application logic and order access, Gemini interprets customer intent, and H2 provides predictable assessment data.

This separation improves testability and makes it easier to change the UI, model provider or database independently. Most importantly, it prevents model-generated text from becoming the authority for business rules.

### Q2. Why not make a single request to an LLM and let it answer?

A single request would be simpler, but the model would not have authoritative access to order records unless those records were supplied or retrieved through a controlled mechanism. It could produce an incorrect status or an unsupported policy exception.

Function calling allows the model to request a specific backend operation. Spring Boot retrieves the actual record and validates the operation before the result is used in a customer-facing response.

### Q3. Why use native function calling instead of an agent framework?

The assessment needs a small set of tools and a relatively straightforward request-response workflow. Native function calling with explicit Spring Boot services provides sufficient orchestration without introducing an additional framework and its configuration overhead.

A dedicated agent framework may become useful if the application grows to include many tools, complex multi-step workflows, reusable orchestration patterns or more advanced agent observability.

### Q4. Why use a relational database for three mock orders?

Even a small dataset benefits from a structured source of truth. A relational database supports predictable retrieval and clear separation between conversational text and actual order data.

H2 keeps the assessment easy to run. For production, I would use a managed database with persistent storage, migrations, backups, access controls and transaction management.

### Q5. How do you reduce hallucinations?

The system retrieves order information through backend tools instead of asking the model to invent it. Policy-sensitive eligibility checks are handled by application logic, and unknown orders should produce explicit not-found results.

The model should acknowledge when information is unavailable. These controls reduce hallucination risk but do not guarantee that every generated sentence is correct. Additional response validation, evaluation datasets and monitoring would be needed for stronger assurance.

### Q6. How is cancellation handled safely?

The backend determines whether cancellation is permitted based on the order's actual status and the mock policy. Confirmation and revalidation are necessary before a real state-changing operation.

Cancellation execution is not implemented in this version. Before production use, I would implement transactional cancellation, authorization, idempotency, audit logging and a definitive operation result.

### Q7. Why not connect to a real e-commerce platform?

The assessment supplies a fixed mock dataset. Seeded data makes testing repeatable and avoids external credentials, unpredictable orders and additional integration work.

A production implementation would connect to an authorized order-management API and validate customer identity and order ownership before returning sensitive details or performing actions.

### Q8. Why use REST instead of WebSockets?

The current workflow is based on discrete chat and TTS requests. REST is simple to integrate, inspect and test with tools such as Postman and browser developer tools.

WebSockets or streaming APIs could be introduced if the product required continuous bidirectional audio, partial transcripts or lower-latency interaction. They are not necessary merely to support a request-response chat endpoint.

### Q9. How do you handle provider failures?

The application should avoid treating a provider error as a successful answer. Gemini failures should produce controlled recovery behaviour, while Sarvam failures can trigger browser speech synthesis when supported.

Production improvements would include timeouts, bounded retries, structured error categories, metrics and alerts. Retrying state-changing operations requires idempotency protection to prevent duplicate actions.

### Q10. How would you reduce latency?

I would measure the time spent in input capture, any speech recognition, the Gemini request, tool execution, TTS generation and audio playback.

Potential improvements include reducing unnecessary model calls, keeping tool results small, tuning timeouts, reusing connections, streaming supported responses and reducing cold starts. I would implement the changes that address measured bottlenecks rather than adding complexity without evidence.

### Q11. What would you improve with one additional week?

I would prioritise:
1. Implementing and testing real cancellation execution with authorization, transactions and idempotency.
2. Improving speech recognition and interruption handling.
3. Verifying server-side TTS and fallback behaviour end to end.
4. Strengthening session and customer verification.
5. Expanding integration tests against deployed endpoints.
6. Adding structured logs, latency metrics and provider error monitoring.
7. Improving accessibility and microphone-permission recovery.

These changes address correctness and reliability before adding nonessential features.

### Q12. How would you scale to 1,000 conversations per day?

I would first measure peak concurrent conversations, turns per conversation, token usage, audio duration, latency, provider limits and cost. Daily conversation count alone is not enough to size infrastructure.

Then I would:
- Move in-memory session state to a shared store or persistent session service.
- Use a managed database with suitable indexes and connection pooling.
- Scale backend instances horizontally when measurements justify it.
- Add rate limits, timeouts, bounded retries and request tracing.
- Monitor LLM and TTS usage and cost per conversation.
- Process non-interactive post-call analytics asynchronously.
- Add dashboards, alerts and load tests.

The system should be scaled based on measured bottlenecks and defined latency and reliability targets.

### Q13. What are the main trade-offs?

| Decision | Benefit | Trade-off |
|---|---|---|
| Gemini function calling | Flexible natural-language interaction with structured tool requests | External API latency, cost and provider dependency |
| Spring Boot | Centralized rules, services and API handling | More configuration and deployment work than a minimal server |
| H2 | Quick setup and repeatable mock data | Not a substitute for production persistence |
| REST | Easy integration and testing | Not a complete streaming-audio solution |
| Sarvam TTS | Dedicated speech-generation service | External dependency and potential latency |
| Browser speech fallback | Basic spoken responses during provider failure | Inconsistent voices and quality across devices |
| Separate frontend and backend hosting | Independent deployments | Requires correct CORS and environment configuration |

---

## 12. Scaling and Production Readiness

A production version would require additional work beyond the assessment implementation.

### Reliability
- Provider timeouts and bounded retries
- Health checks and service monitoring
- Graceful recovery from provider failures
- Idempotency for state-changing operations
- Concurrency control for order updates

### Security
- Authentication and authorization
- Customer-to-order ownership verification
- Secret rotation and restricted credentials
- Rate limiting and abuse prevention
- Input validation and output safeguards

### Observability
- Request IDs and structured logs
- Latency and error metrics
- Provider availability and cost monitoring
- Audit trails for business actions
- Redaction of sensitive customer information

### Data and infrastructure
- Persistent managed database
- Database migrations and backups
- Shared session storage when horizontally scaling
- Load testing and capacity planning
- Appropriate privacy and data-retention controls

These are proposed production improvements, not claims that every capability is already implemented.

## 13. Limitations

- Orders and tracking references are seeded demonstration data.
- Courier tracking is not connected to live carrier systems.
- Cancellation execution is not implemented.
- Sarvam TTS depends on valid credentials, provider availability and correct response handling.
- Browser speech synthesis varies by device and browser.
- Speech-recognition capabilities must be described according to the actual deployed implementation.
- Production use would require stronger authentication, authorization, persistence, observability and privacy controls.

## 14. Security Notes

- `GEMINI_API_KEY` and `SARVAM_API_KEY` must remain on the backend.
- Never commit real API keys or deployment credentials.
- Configure CORS to allow only intended frontend origins.
- Validate tool arguments on the server.
- Do not treat LLM output as a security boundary.
- Verify customer identity and order ownership before exposing sensitive information.
- Protect state-changing operations with authorization, revalidation, idempotency and auditability.
- Use appropriate logging, rate limits, privacy controls and secret rotation before production deployment.

## 15. Project Structure

The application is split into separate frontend and backend repositories/directories.

**Backend responsibilities**
- REST API endpoints
- Gemini integration and function calling
- Order retrieval and policy checks
- H2 data access
- Sarvam speech generation
- Environment-based configuration
- Backend tests

**Frontend responsibilities**
- Call controls and conversation UI
- Microphone and audio interaction
- Chat API integration
- Listening, thinking and speaking states
- Transcript and call summary presentation
- Browser speech-synthesis fallback
- Frontend tests

Refer to the source repositories for the exact package and file structure.

## 16. Project Links

- **Live application:** https://aura-skin-care-frontend.vercel.app
- **Backend API:** https://aura-voice-cx-agent.onrender.com
- **Backend health check:** https://aura-voice-cx-agent.onrender.com/health
- **GitHub repository:** https://github.com/ganesh-hirve/aura-voice-cx-agent
- **Developer LinkedIn:** https://www.linkedin.com/in/ganesh-hirve-952609298

---

## 17. Assessment Summary

This project demonstrates a browser-based voice customer-support workflow that connects an interactive frontend to a Java backend, uses an LLM for conversational reasoning and function calling, retrieves seeded order information, and applies backend-owned business rules.

The primary engineering principle is that **the LLM interprets the customer's intent, while the backend remains responsible for authoritative data and business decisions**. The assessment version provides a foundation for further work on transactional actions, speech reliability, observability, security and production scaling.
