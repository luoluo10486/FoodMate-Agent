"""Minimal Python runtime stub for exercising the Java runtime gateway."""

import argparse
import json
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta


def post(base_url, path, payload, token=""):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers={"Content-Type": "application/json", "X-Runtime-Token": token},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def run_once(base_url, run_id, token):
    deadline = (datetime.now(timezone.utc) + timedelta(minutes=1)).isoformat()
    command = {
        "dispatchId": "stub-dispatch-" + run_id,
        "runId": run_id,
        "input": "local stub run",
        "deadlineAt": deadline,
        "attempt": 1,
    }
    post(base_url, "/internal/runtime/runs:dispatch", command, token)
    events = [
        ("stub-event-1", 1, "DISPATCHED", None),
        ("stub-event-2", 2, "RUNNING", None),
        ("stub-event-3", 3, "SUCCEEDED", {"answer": "stub completed"}),
    ]
    for event_id, sequence, state, payload in events:
        result = post(
            base_url,
            "/internal/runtime/runs:events",
            {
                "eventId": event_id,
                "runId": run_id,
                "eventSeq": sequence,
                "state": state,
                "payload": payload,
                "occurredAt": datetime.now(timezone.utc).isoformat(),
            },
            token,
        )
        print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the local FoodMate Agent runtime stub")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--run-id", default="stub-run-1")
    parser.add_argument("--token", default="")
    args = parser.parse_args()
    try:
        run_once(args.base_url, args.run_id, args.token)
    except urllib.error.URLError as error:
        raise SystemExit(f"runtime gateway unavailable: {error}") from error
