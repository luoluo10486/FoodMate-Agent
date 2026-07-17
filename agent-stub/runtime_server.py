"""Small standard-library Agent Runtime server for local Java integration."""

import json
import os
import threading
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime, timezone


JAVA_CALLBACK_URL = os.getenv("JAVA_CALLBACK_URL", "http://localhost:8080")


def emit(command, event_id, sequence, state, payload=None):
    body = json.dumps({
        "event_id": event_id,
        "run_id": command["run_id"],
        "event_seq": sequence,
        "state": state,
        "payload": payload,
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }).encode("utf-8")
    request = urllib.request.Request(
        JAVA_CALLBACK_URL.rstrip("/") + "/internal/runtime/runs:events",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10):
        pass


def execute(command):
    prefix = command["dispatch_id"]
    emit(command, prefix + "-accepted", 1, "DISPATCHED")
    emit(command, prefix + "-running", 2, "RUNNING")
    emit(command, prefix + "-completed", 3, "SUCCEEDED", {"answer": "Python runtime completed"})


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/internal/runtime/runs:dispatch":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        command = json.loads(self.rfile.read(length))
        threading.Thread(target=execute, args=(command,), daemon=True).start()
        response = json.dumps({"accepted": True, "dispatch_id": command["dispatch_id"]}).encode("utf-8")
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    port = int(os.getenv("PORT", "9000"))
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
