# FoodMate Agent Runtime

Python owns agent execution only. Business data, authorization decisions, and writes remain in Java.

Run locally with `python runtime_server.py`. The service listens on `127.0.0.1:9000` by default and exposes `/health/live`, `/health/ready`, dispatch, and cancel endpoints.

Required production configuration: `JAVA_CALLBACK_URL`, `FOODMATE_RUNTIME_TOKEN`, and `FOODMATE_CONTRACT_VERSION`.

