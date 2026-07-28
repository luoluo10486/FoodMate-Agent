# FoodMate Agent Runtime

Python owns agent execution only. Business data, authorization decisions, and writes remain in Java.

Run locally with `python runtime_server.py`. The service listens on `127.0.0.1:9000` by default and exposes `/foodmate/internal/health/live`, `/foodmate/internal/health/ready`, V1 dispatch, and cancel endpoints.

Required service configuration: `JAVA_CALLBACK_URL`, `FOODMATE_CONTRACT_VERSION`, `FOODMATE_SERVICE_JWT_ENABLED`, `FOODMATE_PYTHON_PRIVATE_KEY`, `FOODMATE_PYTHON_KID`, and `FOODMATE_JAVA_PUBLIC_KEY`. Python does not receive PostgreSQL business credentials.

M1-4 currently uses a provider-neutral model boundary. By default it selects `deterministic:local`, emits `run.model_usage` before evaluated answer events, and does not make a cloud request. A real model uses `FOODMATE_MODEL_TIER_<HIGH|STANDARD|ECONOMY|EVAL>=provider_id:model_name` plus `FOODMATE_MODEL_PROVIDER_<PROVIDER_ID>_BASE_URL` and `..._API_KEY`. Any number of OpenAI Chat Completions compatible endpoints can be configured; actual provider credentials are intentionally not stored in this repository.

Complex requests run an independent `eval` call before answer streaming. Low-risk requests use stable sampling. Set `FOODMATE_AGENT_CHECKPOINT_BACKEND=redis` and provide `FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_KEY` to enable the Redis CAS checkpoint backend; the local default remains `inmemory` for tests.

Workflow budgets are configured with `FOODMATE_AGENT_MAX_STEP_RETRIES` (default `2`), `FOODMATE_AGENT_MAX_REPLANS` (default `1`), `FOODMATE_AGENT_MAX_ANSWER_REWRITES` (default `1`), `FOODMATE_AGENT_MAX_TOTAL_STEPS` (default `30`), and `FOODMATE_AGENT_MAX_MODEL_CALLS` (default `12`). The current runtime consumes the model-call budget and exposes fixed 70%/85%/100% actions; full LangGraph loop counters remain pending.
