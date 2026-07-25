# FoodMate Agent Runtime

Python owns agent execution only. Business data, authorization decisions, and writes remain in Java.

Run locally with `python runtime_server.py`. The service listens on `127.0.0.1:9000` by default and exposes `/foodmate/internal/health/live`, `/foodmate/internal/health/ready`, V1 dispatch, and cancel endpoints.

Required service configuration: `JAVA_CALLBACK_URL`, `FOODMATE_CONTRACT_VERSION`, `FOODMATE_SERVICE_JWT_ENABLED`, `FOODMATE_PYTHON_PRIVATE_KEY`, `FOODMATE_PYTHON_KID`, and `FOODMATE_JAVA_PUBLIC_KEY`. Python does not receive PostgreSQL business credentials.

M1-3 uses a deterministic stub. It emits `run.accepted`, `run.routed`, two `run.answer_stream` events, and `run.completed`; the text is marked as development verification output and is not a model answer.
