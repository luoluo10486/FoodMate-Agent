# FoodMate Agent Runtime

Python owns agent execution only. Business data, authorization decisions, and writes remain in Java.

Run locally with `python runtime_server.py`. The service listens on `127.0.0.1:9000` by default and exposes `/foodmate/internal/health/live`, `/foodmate/internal/health/ready`, V1 dispatch, and cancel endpoints.

Required service configuration: `JAVA_CALLBACK_URL`, `FOODMATE_CONTRACT_VERSION`, `FOODMATE_SERVICE_JWT_ENABLED`, `FOODMATE_PYTHON_PRIVATE_KEY`, `FOODMATE_PYTHON_KID`, and `FOODMATE_JAVA_PUBLIC_KEY`. Python does not receive PostgreSQL business credentials.

M1-4 currently uses a provider-neutral model boundary. By default it selects `deterministic:local`, emits `run.model_usage` and then an independent `run.eval_decided` gate fact before evaluated answer events, and does not make a cloud request. A real model uses `FOODMATE_MODEL_TIER_<HIGH|STANDARD|ECONOMY|EVAL>=provider_id:model_name` plus `FOODMATE_MODEL_PROVIDER_<PROVIDER_ID>_BASE_URL` and `..._API_KEY`. Any number of OpenAI Chat Completions compatible endpoints can be configured; actual provider credentials are intentionally not stored in this repository.

Complex requests run an independent `eval` call before answer streaming. Low-risk requests use stable sampling. Set `FOODMATE_AGENT_CHECKPOINT_BACKEND=redis` and provide `FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_KEY` to enable the Redis CAS checkpoint backend; the local default remains `inmemory` for tests.

Answer events use UTF-8 byte-limited chunks (`FOODMATE_AGENT_STREAM_CHUNK_MAX_BYTES`, default `2048`) and a configurable interval between chunks (`FOODMATE_AGENT_STREAM_CHUNK_INTERVAL_MS`, default `150`). The interval is applied between complete `run.answer_stream` events; the runtime never publishes one RocketMQ message per model token. Set the interval to `0` only for deterministic unit tests or an explicitly chosen local integration profile.

M2-1 RAG has two explicit local paths. `FOODMATE_RAG_MODE=stub` uses the shared Redis deterministic keyword index and does not connect to Milvus or read embedding credentials. `FOODMATE_RAG_MODE=local` writes vectors to Milvus; set `FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic` for a stable in-process vector provider without a paid API, or set it to `openai-compatible` and provide the endpoint, API key, model, budgets, and price version. A missing real-provider setting fails closed and never falls back to another provider.

Offline Eval regression cases live in `eval/golden_cases.json` and are checked by `tests/test_golden_eval.py`. The rubric asserts routing, risk policy, Eval outcome, required answer fragments, and model-call scenes. Run `python -m pytest -q` from `agent-runtime`; this suite does not require a model credential and keeps the local Judge stub.

To enable a real Judge, configure `FOODMATE_MODEL_TIER_EVAL=provider:model` together with `FOODMATE_MODEL_PROVIDER_<PROVIDER>_BASE_URL`, `..._API_KEY`, audited input/output prices, and `FOODMATE_MODEL_PROVIDER_<PROVIDER>_..._PRICE_VERSION`. Set `FOODMATE_AGENT_EVAL_MIN_SCORE` to the approved pass threshold (default `0.75`). Invalid Judge JSON, non-boolean `passed`, non-finite/out-of-range scores, provider failures, and scores below the threshold fail closed to a degraded answer.

Human calibration samples are stored in `eval/calibration_samples.json`. Each sample must be reviewed by a human before changing `review_status` to `reviewed` and setting `human_label` to `pass` or `degrade`; the repository does not treat pending samples as calibration evidence.

Workflow budgets are configured with `FOODMATE_AGENT_MAX_STEP_RETRIES` (default `2`), `FOODMATE_AGENT_MAX_REPLANS` (default `1`), `FOODMATE_AGENT_MAX_ANSWER_REWRITES` (default `1`), `FOODMATE_AGENT_MAX_TOTAL_STEPS` (default `30`), and `FOODMATE_AGENT_MAX_MODEL_CALLS` (default `12`). The current runtime consumes the model-call budget and exposes fixed 70%/85%/100% actions; full LangGraph loop counters remain pending.

The effective model is the process environment, including the repository-root `.env` loaded at startup. The code and `docker/.env.example` default to `deterministic:local`; setting `FOODMATE_MODEL_TIER_STANDARD`, `..._HIGH`, or `..._EVAL` to a cloud alias is an explicit opt-in and can make a real request. Do not infer the effective model from this README alone.

`GET /foodmate/internal/health/ready` reports checkpoint backend, Redis, RocketMQ producer/consumer startup state, and in-process Eval metrics. Coordination failure returns HTTP `503` with `RUNTIME_COORDINATION_UNAVAILABLE`. Eval metrics include pass/degrade/provider-failure/schema-invalid rates and P95/P99 gate latency; they are operational process metrics, not a production billing ledger.

At resumable `tool_wait`/`execution` boundaries, the Runtime saves a CAS checkpoint and emits the non-terminal `run.checkpoint_saved` event. The event contains only reconciliation metadata. Java persists that event in PostgreSQL Inbox and the authenticated `POST /api/agent-runs/{runId}/recover-from-checkpoint` entry point creates a new dispatch attempt from the persisted fact; it never reuses the old dispatch or trusts browser-supplied checkpoint metadata.

The Eval regression suite currently covers deterministic Golden cases, Judge schema fail-closed behavior, provider failures, safety degradation, and in-process P95/P99 metrics. The runtime emits only gate metadata (`result`, `reason`, `score`, `evaluator_version`), never the candidate answer in the Eval event. The current project environment runs `56 passed, 1 skipped`. This is a local quality gate and regression baseline, not a production quality claim; production calibration still requires reviewed samples and an external metrics/billing system.
