"""FoodMate Agent Runtime V1, dependency-free local implementation."""

import json
import os
import threading
import urllib.error
import urllib.request
import base64
import uuid
import traceback
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from runtime_env import load_project_env
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
load_project_env()

from agent_core import InMemoryCheckpoint, run_deterministic, split_answer
from model_provider import ModelProviderError
from recovery_protocol import checkpoint_digest, validate_recovery_command

JAVA_CALLBACK_URL = os.getenv("JAVA_CALLBACK_URL", "http://localhost:8080")
CONTRACT_VERSION = os.getenv("FOODMATE_CONTRACT_VERSION", "v1")
JWT_ENABLED = os.getenv("FOODMATE_SERVICE_JWT_ENABLED", "true").lower() == "true"
PYTHON_PRIVATE_KEY = os.getenv("FOODMATE_PYTHON_PRIVATE_KEY", "")
PYTHON_KID = os.getenv("FOODMATE_PYTHON_KID", "")
JAVA_PUBLIC_KEY = os.getenv("FOODMATE_JAVA_PUBLIC_KEY", "")
STATE_FILE = os.getenv("FOODMATE_RUNTIME_STATE_FILE", "")
_cancelled: set[str] = set()
_dispatches: dict[str, dict] = {}
_lock = threading.Lock()
_event_publisher = None
_proposal_publisher = None
_result_waiters: dict[str, dict] = {}
_result_condition = threading.Condition(_lock)
def _new_checkpoint():
    # 本地默认内存后端；启用 Redis 时必须同时配置 checkpoint 加密密钥。
    if os.getenv("FOODMATE_AGENT_CHECKPOINT_BACKEND", "inmemory").lower() == "redis":
        from mq_runtime import RedisCheckpoint
        return RedisCheckpoint()
    return InMemoryCheckpoint()


_checkpoint = _new_checkpoint()


def _canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str).encode("utf-8")


def _digest(value):
    import hashlib
    return "sha256:" + hashlib.sha256(_canonical(value)).hexdigest()


def _headers():
    headers = {"Content-Type": "application/json", "X-Contract-Version": CONTRACT_VERSION}
    if JWT_ENABLED:
        headers["Authorization"] = "Bearer " + _sign("foodmate-agent-runtime", "foodmate-control-plane", "agent:event")
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
    if not JWT_ENABLED:
        return True
    if not JAVA_PUBLIC_KEY:
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


def emit(command, event_id, sequence, event_type, payload=None):
    # Runtime 只回传协议事件，不直接写 FoodMate 业务表；状态投影由 Java 完成。
    request_id = "req_evt_" + uuid.uuid4().hex
    occurred_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    stable = {
        "schema_version": CONTRACT_VERSION,
        "run_id": command["run_id"],
        "dispatch_id": command["dispatch_id"],
        "attempt": command["attempt"],
        "event_id": event_id,
        "event_seq": sequence,
        "occurred_at": occurred_at,
        "event_type": event_type,
        "payload": payload or {},
    }
    body = json.dumps({
        "schema_version": CONTRACT_VERSION,
        "event_id": event_id,
        "run_id": command["run_id"],
        "dispatch_id": command["dispatch_id"],
        "attempt": command["attempt"],
        "event_seq": sequence,
        "request_id": request_id,
        "trace_id": command.get("trace_id", "trace_stub"),
        "request_hash": _digest(stable),
        "occurred_at": occurred_at,
        "event_type": event_type,
        "payload": payload or {},
    }).encode("utf-8")
    if _event_publisher is not None:
        _event_publisher.publish(json.loads(body.decode("utf-8")))
        return
    request = urllib.request.Request(
        JAVA_CALLBACK_URL.rstrip("/") + "/foodmate/internal/v1/agent-events",
        data=body,
        headers=_headers(),
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10):
        pass


def _on_result(result: dict):
    """只按 proposal_id 暂存一次结果，真正的业务幂等由 Redis Result Inbox 保证。"""
    proposal_id = str(result.get("proposal_id", ""))
    if not proposal_id:
        return
    with _result_condition:
        _result_waiters[proposal_id] = result
        _result_condition.notify_all()


def _await_result(proposal_id: str, timeout_seconds: float) -> dict:
    end = datetime.now(timezone.utc).timestamp() + timeout_seconds
    with _result_condition:
        while proposal_id not in _result_waiters:
            remaining = end - datetime.now(timezone.utc).timestamp()
            if remaining <= 0:
                raise TimeoutError("TOOL_RESULT_TIMEOUT")
            _result_condition.wait(remaining)
        return _result_waiters.pop(proposal_id)


def _save_tool_wait_checkpoint(command: dict, proposals: list[dict]) -> None:
    """Persist the only resumable boundary before a Java-owned tool invocation."""
    budget = ((command.get("runtime_options") or {}).get("budget_snapshot") or command.get("budget_snapshot") or {})
    checkpoint = {
        "schema_version": "v1",
        "workflow_version": "foodmate-m1-4-v1",
        "prompt_version": str((command.get("runtime_options") or {}).get("prompt_set_version", "")),
        "run_id": str(command["run_id"]),
        "dispatch_id": str(command["dispatch_id"]),
        "attempt": int(command["attempt"]),
        "current_node": "tool_wait",
        "deadline_at": command["deadline_at"],
        "budget_revision": int(budget.get("revision", 1)),
        "completed_invocation_ids": [],
        "pending_proposals": proposals,
        "event_seq": 1,
    }
    _checkpoint.save(f"{command['run_id']}:{command['dispatch_id']}", checkpoint)


def _mark_tool_results_applied(command: dict, results: list[dict]) -> None:
    """Advance the resumable checkpoint before the follow-up Composer call."""
    key = f"{command['run_id']}:{command['dispatch_id']}"
    loaded = _checkpoint.load(key)
    if loaded is None:
        raise RuntimeError("CHECKPOINT_NOT_FOUND")
    version, checkpoint = loaded
    checkpoint = dict(checkpoint)
    checkpoint["current_node"] = "execution"
    checkpoint["completed_invocation_ids"] = sorted(
        {str(item["invocation_id"]) for item in results if item.get("invocation_id")}
    )
    checkpoint["pending_proposals"] = []
    checkpoint["event_seq"] = 2
    _checkpoint.save(key, checkpoint, version)


def execute(command):
    prefix = command["dispatch_id"]
    try:
        emit(command, prefix + "-accepted", 1, "run.accepted", {"status": "queued"})
        if command["run_id"] in _cancelled:
            emit(command, prefix + "-cancel-ack", 2, "run.cancel_acknowledged", {"reason": "user_requested"})
            emit(command, prefix + "-cancelled", 3, "run.cancelled", {"reason": "user_requested"})
            return
        recovered = validate_recovery_command(command, _checkpoint)
        if recovered is not None:
            authorized = dict(command.get("authorized_context") or {})
            completed_results = (command.get("recovery_context") or {}).get("completed_tool_results") or []
            if completed_results:
                authorized["tool_results"] = completed_results
            command = dict(command)
            command["authorized_context"] = authorized
        execution = run_deterministic(command, _checkpoint)
        if execution.proposals:
            if _proposal_publisher is None:
                raise RuntimeError("TOOL_RUNTIME_UNAVAILABLE")
            _save_tool_wait_checkpoint(command, execution.proposals)
            results = []
            for proposal in execution.proposals:
                _proposal_publisher.publish(proposal)
                results.append(_await_result(
                    proposal["proposal_id"],
                    float(os.getenv("FOODMATE_AGENT_TOOL_RESULT_TIMEOUT_SECONDS", "30")),
                ))
            _mark_tool_results_applied(command, results)
            resumed = dict(command)
            authorized = dict(resumed.get("authorized_context") or {})
            authorized["tool_results"] = results
            resumed["authorized_context"] = authorized
            follow_up = run_deterministic(resumed, _checkpoint)
            follow_up.model_attempts = execution.model_attempts + follow_up.model_attempts
            follow_up.usage.tokens += execution.usage.tokens
            follow_up.usage.cost_cny += execution.usage.cost_cny
            follow_up.usage.model_calls += execution.usage.model_calls
            execution = follow_up
        emit(command, prefix + "-routed", 2, "run.routed", {
            "status": "routed", "intent": execution.route.intent,
            "complexity": execution.route.complexity, "risk_level": execution.route.risk_level,
            "plan_version": execution.plan.plan_version,
            "workflow": execution.workflow,
        })
        next_sequence = 3
        for index, attempt in enumerate(execution.model_attempts, start=1):
            emit(command, prefix + f"-model-{index}", next_sequence, "run.model_usage", attempt.event_payload())
            next_sequence += 1
        if command["run_id"] in _cancelled:
            emit(command, prefix + "-cancel-ack", next_sequence, "run.cancel_acknowledged", {"reason": "user_requested"})
            emit(command, prefix + "-cancelled", next_sequence + 1, "run.cancelled", {"reason": "user_requested"})
            return
        answer = execution.answer
        if execution.eval.result == "pass":
            stream_chunks = split_answer(answer, int(os.getenv("FOODMATE_AGENT_STREAM_CHUNK_MAX_BYTES", "2048")))
        else:
            stream_chunks = []
        for index, chunk in enumerate(stream_chunks, start=1):
            emit(command, prefix + f"-answer-{index}", next_sequence, "run.answer_stream", {"text": chunk, "status": "evaluated"})
            next_sequence += 1
        if command["run_id"] in _cancelled:
            emit(command, prefix + "-cancel-ack", next_sequence, "run.cancel_acknowledged", {"reason": "user_requested"})
            emit(command, prefix + "-cancelled", next_sequence + 1, "run.cancelled", {"reason": "user_requested"})
            return
        emit(command, prefix + "-completed", next_sequence, "run.completed", {
            "answer": answer, "status": "completed", "eval_result": execution.eval.result,
            "eval_reason": execution.eval.reason, "budget_mode": execution.budget_mode,
            "result_type": "normal" if execution.eval.result == "pass" else "safety_degraded",
            "requires_confirmation": bool(execution.budget_actions.get("requires_confirmation", False)),
            "budget_actions": execution.budget_actions,
            "workflow": execution.workflow,
            "usage": execution.usage.__dict__, "memory_candidates": execution.memory_candidates,
            "proposals": execution.proposals,
        })
    except ModelProviderError as error:
        # 模型失败也必须回到 Java 状态机，不能由 Runtime 静默吞掉。
        next_sequence = 3
        for index, attempt in enumerate(error.attempts, start=1):
            emit(command, prefix + f"-model-{index}", next_sequence, "run.model_usage", attempt.event_payload())
            next_sequence += 1
        emit(command, prefix + "-failed", next_sequence, "run.failed", {"code": error.code, "retryable": error.retryable})
    except TimeoutError as error:
        emit(command, prefix + "-failed", 3, "run.failed", {"code": str(error), "retryable": True})
    except urllib.error.URLError:
        # 超时和重试由 Java 控制面负责，Runtime 不直接写业务状态。
        return
    except Exception as error:
        # 未预期异常也必须留下终态事件，避免 Java/前端永久停在 routed。
        print(f"runtime execution failed run_id={command.get('run_id')} error={type(error).__name__}: {error}", flush=True)
        traceback.print_exc()
        try:
            emit(command, prefix + "-failed", 3, "run.failed", {"code": "RUNTIME_EXECUTION_FAILED", "retryable": False})
        except Exception:
            traceback.print_exc()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in {"/foodmate/internal/health/live", "/foodmate/internal/health/ready"}:
            self.send_error(404)
            return
        self._json(200, {"status": "UP", "contract_version": CONTRACT_VERSION})

    def do_POST(self):
        is_dispatch = self.path == "/foodmate/internal/v1/runs"
        is_cancel = self.path.startswith("/foodmate/internal/v1/runs/") and self.path.endswith("/cancel")
        if not is_dispatch and not is_cancel:
            self.send_error(404)
            return
        if not self._authenticated() or self.headers.get("X-Contract-Version", CONTRACT_VERSION) != CONTRACT_VERSION:
            self._json(401, {"code": "RUNTIME_AUTH_INVALID"})
            return
        try:
            command = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
            if is_dispatch:
                self._dispatch(command)
            else:
                self._cancel(command, self.path.split("/")[-2])
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

    def _cancel(self, command, path_run_id):
        if "run_id" not in command or "cancel_id" not in command:
            raise KeyError("run_id/cancel_id")
        if command["run_id"] != path_run_id:
            self._json(409, {"code": "RUNTIME_STATE_CONFLICT"})
            return
        with _lock:
            _cancelled.add(command["run_id"])
        self._json(202, {"accepted": True, "cancel_id": command["cancel_id"]})

    def _authenticated(self):
        # Local development can intentionally disable service JWT; production keeps
        # the normal Bearer verification path below.
        if not JWT_ENABLED:
            return True
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            return False
        required_scope = "runtime:dispatch" if self.path.endswith("/runs") else "runtime:cancel"
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
    transport = os.getenv("FOODMATE_AGENT_TRANSPORT", "http").lower()
    mq_runtime = None
    if transport == "rocketmq":
        from mq_runtime import RocketMqEventPublisher, RocketMqProposalPublisher, RocketMqRuntime
        _event_publisher = RocketMqEventPublisher()
        _proposal_publisher = RocketMqProposalPublisher()
        mq_runtime = RocketMqRuntime(execute, publisher=_event_publisher, proposal_publisher=_proposal_publisher, on_result=_on_result)
        mq_runtime.start()
    try:
        ThreadingHTTPServer(("127.0.0.1", int(os.getenv("PORT", "9000"))), Handler).serve_forever()
    finally:
        if mq_runtime is not None:
            mq_runtime.close()
