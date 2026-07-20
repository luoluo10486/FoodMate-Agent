"""FoodMate Agent Runtime V1, dependency-free local implementation."""

import json
import os
import threading
import urllib.error
import urllib.request
import base64
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

JAVA_CALLBACK_URL = os.getenv("JAVA_CALLBACK_URL", "http://localhost:8080")
CONTRACT_VERSION = os.getenv("FOODMATE_CONTRACT_VERSION", "v1")
JWT_ENABLED = os.getenv("FOODMATE_SERVICE_JWT_ENABLED", "true").lower() == "true"
PYTHON_PRIVATE_KEY = os.getenv("FOODMATE_PYTHON_PRIVATE_KEY", "")
PYTHON_KID = os.getenv("FOODMATE_PYTHON_KID", "")
JAVA_PUBLIC_KEY = os.getenv("FOODMATE_JAVA_PUBLIC_KEY", "")
_cancelled: set[str] = set()
_dispatches: dict[str, dict] = {}
_lock = threading.Lock()


def _headers():
    headers = {"Content-Type": "application/json", "X-Contract-Version": CONTRACT_VERSION}
    if JWT_ENABLED:
        headers["Authorization"] = "Bearer " + _sign("foodmate-agent-runtime", "foodmate-control-plane", "runtime:event")
    return headers


def _b64(value):
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def _decode(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _sign(issuer, audience, scope):
    if not PYTHON_PRIVATE_KEY or not PYTHON_KID:
        raise ValueError("Python service JWT signing key is not configured")
    now = int(datetime.now(timezone.utc).timestamp())
    header = _b64(json.dumps({"alg": "EdDSA", "typ": "JWT", "kid": PYTHON_KID}, separators=(",", ":")).encode())
    payload = _b64(json.dumps({"iss": issuer, "sub": issuer, "aud": audience, "scope": scope, "iat": now, "exp": now + 60, "jti": str(uuid.uuid4())}, separators=(",", ":")).encode())
    unsigned = f"{header}.{payload}".encode("ascii")
    key = serialization.load_der_private_key(base64.b64decode(PYTHON_PRIVATE_KEY), password=None)
    return unsigned.decode("ascii") + "." + _b64(key.sign(unsigned))


def _verify(token, issuer, audience, scope):
    if not JWT_ENABLED or not JAVA_PUBLIC_KEY:
        return False
    try:
        header, payload, signature = token.split(".")
        header_json = json.loads(_decode(header))
        claims = json.loads(_decode(payload))
        if header_json.get("alg") != "EdDSA" or not header_json.get("kid"):
            return False
        key = serialization.load_der_public_key(base64.b64decode(JAVA_PUBLIC_KEY))
        key.verify(_decode(signature), f"{header}.{payload}".encode("ascii"))
        return claims.get("iss") == issuer and claims.get("aud") == audience and scope in claims.get("scope", "").split() and claims.get("exp", 0) > int(datetime.now(timezone.utc).timestamp()) and bool(claims.get("jti"))
    except Exception:
        return False


def emit(command, event_id, sequence, state, payload=None):
    body = json.dumps({
        "schema_version": CONTRACT_VERSION,
        "event_id": event_id,
        "run_id": command["run_id"],
        "dispatch_id": command["dispatch_id"],
        "event_seq": sequence,
        "state": state,
        "payload": payload or {},
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }).encode("utf-8")
    request = urllib.request.Request(
        JAVA_CALLBACK_URL.rstrip("/") + "/internal/runtime/runs:events",
        data=body,
        headers=_headers(),
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10):
        pass


def execute(command):
    prefix = command["dispatch_id"]
    try:
        emit(command, prefix + "-accepted", 1, "DISPATCHED")
        if command["run_id"] in _cancelled:
            emit(command, prefix + "-cancelled", 2, "CANCELED", {"reason": "cancelled"})
            return
        emit(command, prefix + "-running", 2, "RUNNING")
        if command["run_id"] in _cancelled:
            emit(command, prefix + "-cancelled", 3, "CANCELED", {"reason": "cancelled"})
            return
        emit(command, prefix + "-completed", 3, "SUCCEEDED", {"answer": "Python runtime completed"})
    except (urllib.error.URLError, TimeoutError):
        # Java owns the timeout/retry policy. The runtime must not write business state directly.
        return


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in {"/health/live", "/health/ready"}:
            self.send_error(404)
            return
        self._json(200, {"status": "UP", "contract_version": CONTRACT_VERSION})

    def do_POST(self):
        if self.path not in {"/internal/runtime/runs:dispatch", "/internal/runtime/runs:cancel"}:
            self.send_error(404)
            return
        if not self._authenticated() or self.headers.get("X-Contract-Version", CONTRACT_VERSION) != CONTRACT_VERSION:
            self._json(401, {"code": "RUNTIME_AUTH_INVALID"})
            return
        try:
            command = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
            if self.path.endswith("dispatch"):
                self._dispatch(command)
            else:
                self._cancel(command)
        except (KeyError, ValueError, json.JSONDecodeError):
            self._json(400, {"code": "RUNTIME_CONTRACT_INVALID"})

    def _dispatch(self, command):
        for required in ("run_id", "dispatch_id", "deadline_at", "attempt"):
            if required not in command:
                raise KeyError(required)
        with _lock:
            existing = _dispatches.get(command["dispatch_id"])
            if existing is not None:
                if existing != command:
                    self._json(409, {"code": "RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT"})
                    return
                self._json(202, {"accepted": True, "duplicate": True, "dispatch_id": command["dispatch_id"]})
                return
            _dispatches[command["dispatch_id"]] = command
        threading.Thread(target=execute, args=(command,), daemon=True).start()
        self._json(202, {"accepted": True, "duplicate": False, "dispatch_id": command["dispatch_id"]})

    def _cancel(self, command):
        if "run_id" not in command or "cancel_id" not in command:
            raise KeyError("run_id/cancel_id")
        with _lock:
            _cancelled.add(command["run_id"])
        self._json(202, {"accepted": True, "cancel_id": command["cancel_id"]})

    def _authenticated(self):
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            return False
        required_scope = "runtime:dispatch" if self.path.endswith("dispatch") else "runtime:cancel"
        return _verify(authorization[7:], "foodmate-control-plane", "foodmate-agent-runtime", required_scope)

    def _json(self, status, value):
        body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.getenv("PORT", "9000"))), Handler).serve_forever()
