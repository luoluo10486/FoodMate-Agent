"""RocketMQ/Redis transport for the local Python Agent Runtime.

The runtime never writes FoodMate business tables. Redis records technical
command idempotency, while Java remains the PostgreSQL business authority.
"""

import json
import os
import threading
import base64
from typing import Callable

import redis
from cryptography.fernet import Fernet, InvalidToken
from rocketmq import ClientConfiguration, ConsumeResult, Credentials, FilterExpression, Message, MessageListener, Producer, PushConsumer
from proposal_protocol import Proposal, validate_proposal


def _startup_client_with_timeout(client, name: str, timeout_seconds: float):
    """RocketMQ 客户端可能在 Proxy route 查询中阻塞，启动必须有明确上限。"""
    outcome: list[BaseException] = []

    def startup():
        try:
            client.startup()
        except BaseException as error:
            outcome.append(error)

    thread = threading.Thread(target=startup, name=f"rocketmq-start-{name}", daemon=True)
    thread.start()
    thread.join(timeout_seconds)
    if thread.is_alive():
        raise RuntimeError("RUNTIME_MQ_STARTUP_FAILED")
    if outcome:
        raise RuntimeError("RUNTIME_MQ_STARTUP_FAILED") from outcome[0]


class RedisCheckpoint:
    """Redis technical checkpoint with atomic version CAS, TTL and optional encryption."""

    _CAS_SCRIPT = """
    local current = redis.call('GET', KEYS[1])
    local version = 0
    if current then version = tonumber(current) end
    if ARGV[1] ~= '' and tonumber(ARGV[1]) ~= version then return 0 end
    local next_version = redis.call('INCR', KEYS[1])
    redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
    redis.call('EXPIRE', KEYS[1], ARGV[3])
    return next_version
    """

    def __init__(self, client=None, prefix=None, ttl_seconds=None, encryption_key=None):
        self.client = client or redis.Redis.from_url(
            os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
            decode_responses=True,
        )
        self.prefix = prefix or os.getenv("FOODMATE_AGENT_CHECKPOINT_REDIS_PREFIX", "foodmate:agent:checkpoint")
        self.ttl_seconds = int(ttl_seconds or int(os.getenv("FOODMATE_AGENT_CHECKPOINT_TTL_DAYS", "7")) * 86400)
        self.max_bytes = int(os.getenv("FOODMATE_AGENT_CHECKPOINT_MAX_BYTES", "262144"))
        enabled = os.getenv("FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_ENABLED", "true").lower() == "true"
        raw_key = encryption_key or os.getenv("FOODMATE_AGENT_CHECKPOINT_ENCRYPTION_KEY", "")
        if enabled and not raw_key:
            raise RuntimeError("CHECKPOINT_ENCRYPTION_KEY_MISSING")
        self._cipher = Fernet(raw_key.encode("ascii")) if enabled else None

    def _keys(self, key: str) -> tuple[str, str]:
        safe_key = key.replace("/", "_")
        return f"{self.prefix}:{safe_key}:version", f"{self.prefix}:{safe_key}:value"

    def load(self, key: str) -> tuple[int, dict] | None:
        version_key, value_key = self._keys(key)
        version = self.client.get(version_key)
        value = self.client.get(value_key)
        if version is None or value is None:
            return None
        try:
            payload = self._cipher.decrypt(value.encode("ascii")).decode("utf-8") if self._cipher else value
            return int(version), json.loads(payload)
        except (InvalidToken, ValueError, json.JSONDecodeError) as error:
            raise RuntimeError("CHECKPOINT_DATA_INVALID") from error

    def save(self, key: str, value: dict, expected_version: int | None = None) -> int:
        version_key, value_key = self._keys(key)
        payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        encoded = self._cipher.encrypt(payload.encode("utf-8")).decode("ascii") if self._cipher else payload
        if len(encoded.encode("utf-8")) > self.max_bytes:
            raise RuntimeError("CHECKPOINT_TOO_LARGE")
        expected = "" if expected_version is None else str(expected_version)
        result = self.client.eval(self._CAS_SCRIPT, 2, version_key, value_key, expected, encoded, str(self.ttl_seconds))
        if int(result or 0) == 0:
            raise RuntimeError("CHECKPOINT_CAS_CONFLICT")
        return int(result)


class RedisCommandInbox:
    """Persist command acceptance before executing it, absorbing redelivery."""

    def __init__(self, client=None, prefix=None):
        self.client = client or redis.Redis.from_url(
            os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
            decode_responses=True,
        )
        self.prefix = prefix or os.getenv("FOODMATE_AGENT_REDIS_KEY_PREFIX", "foodmate:agent")
        self.ttl_seconds = int(os.getenv("FOODMATE_AGENT_REDIS_INBOX_RETENTION_SECONDS", "604800"))

    def claim(self, dispatch_id: str, request_hash: str, command: dict) -> str:
        key = f"{self.prefix}:inbox:command:{dispatch_id}"
        value = json.dumps({"request_hash": request_hash, "command": command}, ensure_ascii=False, sort_keys=True)
        if self.client.set(key, value, nx=True, ex=self.ttl_seconds):
            return "claimed"
        existing = self.client.get(key)
        if not existing:
            raise RuntimeError("RUNTIME_COORDINATION_UNAVAILABLE")
        record = json.loads(existing)
        if record.get("request_hash") != request_hash:
            raise ValueError("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT")
        return "duplicate"


class RedisResultInbox:
    """记录 Java Tool Result，重复投递只允许回调一次。"""

    def __init__(self, client=None, prefix=None):
        self.client = client or redis.Redis.from_url(
            os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
            decode_responses=True,
        )
        self.prefix = prefix or os.getenv("FOODMATE_AGENT_REDIS_KEY_PREFIX", "foodmate:agent")
        self.ttl_seconds = int(os.getenv("FOODMATE_AGENT_REDIS_INBOX_RETENTION_SECONDS", "604800"))

    def claim(self, proposal_id: str, request_hash: str, result: dict) -> str:
        key = f"{self.prefix}:inbox:result:{proposal_id}"
        value = json.dumps({"request_hash": request_hash, "result": result}, ensure_ascii=False, sort_keys=True)
        if self.client.set(key, value, nx=True, ex=self.ttl_seconds):
            return "claimed"
        existing = self.client.get(key)
        if not existing:
            raise RuntimeError("RUNTIME_COORDINATION_UNAVAILABLE")
        if json.loads(existing).get("request_hash") != request_hash:
            raise ValueError("RUNTIME_PROPOSAL_IDEMPOTENCY_CONFLICT")
        return "duplicate"


class RocketMqEventPublisher:
    """Publish immutable RunEvent envelopes after local execution emits them."""

    def __init__(self, producer=None, topic=None, outbox=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_EVENT", "foodmate-agent-event-v1")
        self.producer = producer or self._new_producer()
        self.outbox = outbox or RedisEventOutbox()

    @staticmethod
    def _new_producer():
        endpoint = os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081")
        config = ClientConfiguration(endpoint, Credentials())
        producer = Producer(config, topics=[os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_EVENT", "foodmate-agent-event-v1")])
        _startup_client_with_timeout(producer, "event-producer", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
        return producer

    def publish(self, event: dict):
        event_json = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
        entry_id = self.outbox.enqueue(event_json)
        message = Message()
        message.topic = self.topic
        message.body = event_json.encode("utf-8")
        message.keys = event["run_id"]
        message.add_property("foodmate_message_type", "RunEvent")
        message.add_property("foodmate_schema_version", event["schema_version"])
        message.add_property("foodmate_run_id", event["run_id"])
        message.add_property("foodmate_dispatch_id", event["dispatch_id"])
        message.add_property("foodmate_event_id", event["event_id"])
        message.add_property("foodmate_event_seq", str(event["event_seq"]))
        receipt = self.producer.send(message)
        self.outbox.ack(entry_id)
        return receipt

    def close(self):
        self.producer.shutdown()


class RedisProposalOutbox:
    """持久化 Proposal 原文；Broker 未确认前不能删除，重发保持同一 payload。"""

    def __init__(self, client=None, prefix=None):
        self.client = client or redis.Redis.from_url(
            os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
            decode_responses=True,
        )
        self.key = f"{prefix or os.getenv('FOODMATE_AGENT_REDIS_KEY_PREFIX', 'foodmate:agent')}:outbox:proposal"

    def enqueue(self, proposal_json: str) -> str:
        entry_id = self.client.incr(f"{self.key}:sequence")
        self.client.hset(f"{self.key}:{entry_id}", mapping={"payload": proposal_json})
        self.client.rpush(self.key, str(entry_id))
        return str(entry_id)

    def ack(self, entry_id: str):
        self.client.lrem(self.key, 1, entry_id)
        self.client.delete(f"{self.key}:{entry_id}")


class RocketMqProposalPublisher:
    """向 Java Tool Gateway 发布 Proposal；Python 不持有数据库凭据，也不执行 SQL。"""

    def __init__(self, producer=None, topic=None, outbox=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_PROPOSAL", "foodmate-agent-proposal-v1")
        self.producer = producer or self._new_producer()
        self.outbox = outbox or RedisProposalOutbox()

    @staticmethod
    def _new_producer():
        endpoint = os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081")
        producer = Producer(ClientConfiguration(endpoint, Credentials()), topics=[os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_PROPOSAL", "foodmate-agent-proposal-v1")])
        _startup_client_with_timeout(producer, "proposal-producer", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
        return producer

    def publish(self, proposal: Proposal | dict):
        if isinstance(proposal, Proposal):
            validate_proposal(proposal)
            payload = proposal.as_dict()
        else:
            payload = dict(proposal)
            required = Proposal(
                str(payload.get("proposal_id", "")), str(payload.get("run_id", "")),
                str(payload.get("proposal_type", "")), str(payload.get("schema_version", "")),
                dict(payload.get("payload") or {}), bool(payload.get("requires_confirmation", True)),
                str(payload.get("request_hash", "")),
                payload.get("tool_name"), payload.get("confirmation_ref"), payload.get("input"),
            )
            validate_proposal(required)
            payload = required.as_dict()
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        entry_id = self.outbox.enqueue(body)
        message = Message()
        message.topic = self.topic
        message.body = body.encode("utf-8")
        message.keys = payload["run_id"]
        message.add_property("foodmate_message_type", "ToolProposal")
        message.add_property("foodmate_schema_version", payload["schema_version"])
        message.add_property("foodmate_run_id", payload["run_id"])
        message.add_property("foodmate_proposal_id", payload["proposal_id"])
        message.add_property("foodmate_request_hash", payload["request_hash"])
        receipt = self.producer.send(message)
        self.outbox.ack(entry_id)
        return receipt

    def close(self):
        self.producer.shutdown()


class RocketMqKnowledgeResultPublisher:
    """Publishes only index facts; source bytes and credentials never enter RocketMQ."""
    def __init__(self, producer=None, topic=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_INDEX_RESULT", "foodmate-knowledge-index-result-v1")
        self.producer = producer or self._new_producer()

    def _new_producer(self):
        producer = Producer(ClientConfiguration(os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081"), Credentials()), topics=[self.topic])
        _startup_client_with_timeout(producer, "knowledge-result-producer", float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")))
        return producer

    def publish(self, result: dict):
        message = Message()
        message.topic = self.topic
        message.body = json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        message.keys = str(result["item_id"])
        message.add_property("foodmate_message_type", "KnowledgeIndexResult")
        return self.producer.send(message)

    def close(self):
        self.producer.shutdown()


class _CommandListener(MessageListener):
    def __init__(self, inbox: RedisCommandInbox, execute: Callable[[dict], None], metrics=None):
        self.inbox = inbox
        self.execute = execute
        self.metrics = metrics

    def consume(self, message):
        try:
            command = json.loads(message.body.decode("utf-8"))
            dispatch_id = command["dispatch_id"]
            request_hash = command["request_hash"]
            result = self.inbox.claim(dispatch_id, request_hash, command)
            if result == "claimed":
                self.execute(command)
                if self.metrics:
                    self.metrics("dispatch", "claimed", "redis_inbox")
            elif self.metrics:
                self.metrics("dispatch", "duplicate", "redis_inbox")
            return ConsumeResult.SUCCESS
        except ValueError:
            # Contract/idempotency conflicts are deterministic and must not retry forever.
            if self.metrics:
                self.metrics("dispatch", "rejected", "contract")
            return ConsumeResult.SUCCESS
        except Exception:
            # Redis or execution failures leave the message unacknowledged for RocketMQ retry/DLQ.
            if self.metrics:
                self.metrics("dispatch", "retry", "consumer_error")
            return ConsumeResult.FAILURE


class _ResultListener(MessageListener):
    def __init__(self, inbox: RedisResultInbox, on_result: Callable[[dict], None], metrics=None):
        self.inbox = inbox
        self.on_result = on_result
        self.metrics = metrics

    def consume(self, message):
        try:
            result = json.loads(message.body.decode("utf-8"))
            proposal_id = result["proposal_id"]
            request_hash = result["request_hash"]
            if self.inbox.claim(proposal_id, request_hash, result) == "claimed":
                self.on_result(result)
                if self.metrics:
                    self.metrics("result", "claimed", "redis_inbox")
            elif self.metrics:
                self.metrics("result", "duplicate", "redis_inbox")
            return ConsumeResult.SUCCESS
        except ValueError:
            if self.metrics:
                self.metrics("result", "rejected", "contract")
            return ConsumeResult.SUCCESS
        except Exception:
            if self.metrics:
                self.metrics("result", "retry", "consumer_error")
            return ConsumeResult.FAILURE


class RedisEventOutbox:
    """Keep an event until the Broker confirms it; resend is intentionally idempotent."""

    def __init__(self, client=None, prefix=None):
        self.client = client or redis.Redis.from_url(
            os.getenv("FOODMATE_REDIS_URL", "redis://:foodmate-redis-change-me@localhost:6380"),
            decode_responses=True,
        )
        self.key = f"{prefix or os.getenv('FOODMATE_AGENT_REDIS_KEY_PREFIX', 'foodmate:agent')}:outbox:event"

    def enqueue(self, event_json: str) -> str:
        entry_id = self.client.incr(f"{self.key}:sequence")
        self.client.hset(f"{self.key}:{entry_id}", mapping={"payload": event_json})
        self.client.rpush(self.key, str(entry_id))
        return str(entry_id)

    def ack(self, entry_id: str):
        self.client.lrem(self.key, 1, entry_id)
        self.client.delete(f"{self.key}:{entry_id}")


class RocketMqRuntime:
    """Own the Python command consumer and event producer lifecycle."""

    def __init__(self, execute: Callable[[dict], None], inbox=None, publisher=None, proposal_publisher=None, on_result=None, result_inbox=None, metrics=None):
        endpoint = os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081")
        config = ClientConfiguration(endpoint, Credentials())
        self.publisher = publisher or RocketMqEventPublisher()
        self.proposal_publisher = proposal_publisher
        self.inbox = inbox or RedisCommandInbox()
        self.topic = os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_COMMAND", "foodmate-agent-command-v1")
        self.consumer = PushConsumer(
            config,
            os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_AGENT_COMMAND", "foodmate-python-agent-command-v1"),
            _CommandListener(self.inbox, execute, metrics),
            subscription={self.topic: FilterExpression("*")},
            consumption_thread_count=int(os.getenv("FOODMATE_AGENT_WORKER_CONCURRENCY", "1")),
        )
        self.result_consumer = PushConsumer(
            config,
            os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_AGENT_RESULT", "foodmate-python-agent-result-v1"),
            _ResultListener(result_inbox or RedisResultInbox(), on_result or (lambda _result: None), metrics),
            subscription={os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_RESULT", "foodmate-agent-result-v1"): FilterExpression("*")},
            consumption_thread_count=1,
        )
        self._started = False
        self._lock = threading.Lock()

    def start(self):
        with self._lock:
            if self._started:
                return
            timeout_seconds = float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15"))
            _startup_client_with_timeout(self.consumer, "command", timeout_seconds)
            try:
                _startup_client_with_timeout(self.result_consumer, "result", timeout_seconds)
            except Exception:
                self.consumer.shutdown()
                raise
            self._started = True


    def close(self):
        with self._lock:
            if not self._started:
                return
            self.consumer.shutdown()
            self.result_consumer.shutdown()
            self.publisher.close()
            if self.proposal_publisher is not None:
                self.proposal_publisher.close()
            self._started = False

    @property
    def started(self) -> bool:
        """Expose startup state for the Runtime readiness contract."""
        with self._lock:
            return self._started
