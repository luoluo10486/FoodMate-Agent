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


def _shutdown_client(client) -> None:
    """尽力关闭旧客户端，避免失效连接阻塞新客户端接管。"""
    if client is None or not getattr(client, "is_running", True):
        return
    try:
        client.shutdown()
    except Exception:
        # 旧连接可能已由 SDK 自身清理；新连接已经是权威状态。
        return


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
    """发布不可变 RunEvent，并支持连接失效后的 Producer 重建。"""

    def __init__(self, producer=None, topic=None, outbox=None, producer_factory=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_EVENT", "foodmate-agent-event-v1")
        self._producer_lock = threading.RLock()
        self._producer_factory = producer_factory or self._new_producer if producer is None else producer_factory
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
        with self._producer_lock:
            receipt = self.producer.send(message)
            self.outbox.ack(entry_id)
            return receipt

    def reconnect(self) -> bool:
        """重建 Producer；测试注入的固定 Producer 默认不参与重建。"""
        if self._producer_factory is None:
            return False
        replacement = self._producer_factory()
        with self._producer_lock:
            previous = self.producer
            self.producer = replacement
        _shutdown_client(previous)
        return True

    def close(self):
        with self._producer_lock:
            _shutdown_client(self.producer)


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
    """向 Java Tool Gateway 发布 Proposal，并支持 Producer 重建。"""

    def __init__(self, producer=None, topic=None, outbox=None, producer_factory=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_PROPOSAL", "foodmate-agent-proposal-v1")
        self._producer_lock = threading.RLock()
        self._producer_factory = producer_factory or self._new_producer if producer is None else producer_factory
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
        with self._producer_lock:
            receipt = self.producer.send(message)
            self.outbox.ack(entry_id)
            return receipt

    def reconnect(self) -> bool:
        """重建 Proposal Producer；固定注入的测试 Producer 不主动连接网络。"""
        if self._producer_factory is None:
            return False
        replacement = self._producer_factory()
        with self._producer_lock:
            previous = self.producer
            self.producer = replacement
        _shutdown_client(previous)
        return True

    def close(self):
        with self._producer_lock:
            _shutdown_client(self.producer)


class RocketMqKnowledgeResultPublisher:
    """只发布索引事实，连接失效时可重建 Producer。"""
    def __init__(self, producer=None, topic=None, producer_factory=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_INDEX_RESULT", "foodmate-knowledge-index-result-v1")
        self._producer_lock = threading.RLock()
        self._producer_factory = producer_factory or self._new_producer if producer is None else producer_factory
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
        with self._producer_lock:
            return self.producer.send(message)

    def reconnect(self) -> bool:
        """重建索引结果 Producer。"""
        if self._producer_factory is None:
            return False
        replacement = self._producer_factory()
        with self._producer_lock:
            previous = self.producer
            self.producer = replacement
        _shutdown_client(previous)
        return True

    def close(self):
        with self._producer_lock:
            _shutdown_client(self.producer)


class RocketMqKnowledgePurgeResultPublisher:
    """只发布向量清理结果，连接失效时可重建 Producer。"""
    def __init__(self, producer=None, topic=None, producer_factory=None):
        self.topic = topic or os.getenv("FOODMATE_ROCKETMQ_TOPIC_KNOWLEDGE_PURGE_RESULT", "foodmate-knowledge-purge-result-v1")
        self._producer_lock = threading.RLock()
        self._producer_factory = producer_factory or self._new_producer if producer is None else producer_factory
        self.producer = producer or self._new_producer()
        if producer is None:
            pass

    def _new_producer(self):
        producer = Producer(
            ClientConfiguration(os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081"), Credentials()),
            topics=[self.topic],
        )
        _startup_client_with_timeout(
            producer,
            "knowledge-purge-result-producer",
            float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15")),
        )
        return producer

    def publish(self, result: dict):
        message = Message()
        message.topic = self.topic
        message.body = json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        message.keys = str(result["task_id"])
        message.add_property("foodmate_message_type", "KnowledgePurgeResult")
        with self._producer_lock:
            return self.producer.send(message)

    def reconnect(self) -> bool:
        """重建向量清理结果 Producer。"""
        if self._producer_factory is None:
            return False
        replacement = self._producer_factory()
        with self._producer_lock:
            previous = self.producer
            self.producer = replacement
        _shutdown_client(previous)
        return True

    def close(self):
        with self._producer_lock:
            _shutdown_client(self.producer)


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
    """管理 Agent 消费连接，并在 RocketMQ 重启后自动重建客户端。"""

    def __init__(
        self,
        execute: Callable[[dict], None],
        inbox=None,
        publisher=None,
        proposal_publisher=None,
        on_result=None,
        result_inbox=None,
        metrics=None,
        consumer_factory=None,
    ):
        self.publisher = publisher or RocketMqEventPublisher()
        self.proposal_publisher = proposal_publisher
        self.inbox = inbox or RedisCommandInbox()
        self.result_inbox = result_inbox or RedisResultInbox()
        self.execute = execute
        self.on_result = on_result or (lambda _result: None)
        self.metrics = metrics
        self.endpoint = os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081")
        self.topic = os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_COMMAND", "foodmate-agent-command-v1")
        self.result_topic = os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_RESULT", "foodmate-agent-result-v1")
        self.command_group = os.getenv(
            "FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_AGENT_COMMAND",
            "foodmate-python-agent-command-v1",
        )
        self.result_group = os.getenv(
            "FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_AGENT_RESULT",
            "foodmate-python-agent-result-v1",
        )
        self.consumer_factory = consumer_factory or PushConsumer
        self.consumer = None
        self.result_consumer = None
        self._started = False
        self._lock = threading.RLock()
        self._monitor_stop = threading.Event()
        self._monitor_thread = None

    def _build_consumers(self):
        """每次接管都创建全新的 SDK 客户端，因为 shutdown 后不能再次 startup。"""
        configuration = ClientConfiguration(self.endpoint, Credentials())
        command_consumer = self.consumer_factory(
            configuration,
            self.command_group,
            _CommandListener(self.inbox, self.execute, self.metrics),
            subscription={self.topic: FilterExpression("*")},
            consumption_thread_count=int(os.getenv("FOODMATE_AGENT_WORKER_CONCURRENCY", "1")),
        )
        result_consumer = self.consumer_factory(
            configuration,
            self.result_group,
            _ResultListener(self.result_inbox, self.on_result, self.metrics),
            subscription={self.result_topic: FilterExpression("*")},
            consumption_thread_count=1,
        )
        return command_consumer, result_consumer

    @staticmethod
    def _start_pair(command_consumer, result_consumer, timeout_seconds: float):
        started = []
        try:
            _startup_client_with_timeout(command_consumer, "command", timeout_seconds)
            started.append(command_consumer)
            _startup_client_with_timeout(result_consumer, "result", timeout_seconds)
            started.append(result_consumer)
        except Exception:
            for client in reversed(started):
                _shutdown_client(client)
            _shutdown_client(result_consumer)
            raise

    def start(self):
        with self._lock:
            if self._started:
                return
            timeout_seconds = float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15"))
            command_consumer, result_consumer = self._build_consumers()
            self._start_pair(command_consumer, result_consumer, timeout_seconds)
            self.consumer = command_consumer
            self.result_consumer = result_consumer
            self._started = True
            self._start_health_monitor_locked()

    def _start_health_monitor_locked(self):
        if os.getenv("FOODMATE_ROCKETMQ_HEALTHCHECK_ENABLED", "true").lower() != "true":
            return
        interval = float(os.getenv("FOODMATE_ROCKETMQ_HEALTHCHECK_INTERVAL_SECONDS", "5"))
        if interval <= 0:
            raise RuntimeError("RUNTIME_MQ_HEALTHCHECK_INTERVAL_INVALID")
        self._monitor_stop.clear()
        self._monitor_thread = threading.Thread(
            target=self._health_monitor,
            name="rocketmq-runtime-health",
            daemon=True,
        )
        self._monitor_thread.start()

    def _health_monitor(self):
        interval = float(os.getenv("FOODMATE_ROCKETMQ_HEALTHCHECK_INTERVAL_SECONDS", "5"))
        while not self._monitor_stop.wait(interval):
            if not self.ensure_healthy() and self.metrics:
                self.metrics("transport", "retry", "rocketmq_health_unavailable")

    def _client_healthy(self, client, topic: str) -> bool:
        if client is None or not getattr(client, "is_running", False):
            return False
        # rocketmq-python-client has no public health API. Force a route refresh
        # so a cached route cannot hide a dead Proxy channel after a restart.
        refresh_route = getattr(client, "_Client__update_topic_route", None)
        if not callable(refresh_route):
            return True
        try:
            return refresh_route(topic) is not None
        except Exception:
            return False

    def _clients_healthy_locked(self) -> bool:
        return self._client_healthy(self.consumer, self.topic) and self._client_healthy(
            self.result_consumer, self.result_topic
        )

    def _reconnect_locked(self) -> bool:
        if not self._started:
            return False
        timeout_seconds = float(os.getenv("FOODMATE_ROCKETMQ_STARTUP_TIMEOUT_SECONDS", "15"))
        replacement_command, replacement_result = self._build_consumers()
        try:
            self._start_pair(replacement_command, replacement_result, timeout_seconds)
        except Exception:
            return False
        previous_command, previous_result = self.consumer, self.result_consumer
        self.consumer, self.result_consumer = replacement_command, replacement_result
        for publisher in (self.publisher, self.proposal_publisher):
            reconnect = getattr(publisher, "reconnect", None)
            if callable(reconnect):
                try:
                    reconnect()
                except Exception:
                    if self.metrics:
                        self.metrics("transport", "retry", "rocketmq_producer_reconnect_failed")
        _shutdown_client(previous_command)
        _shutdown_client(previous_result)
        return True

    def ensure_healthy(self) -> bool:
        """探测真实路由；失效时新建 Consumer/Producer 后再返回。"""
        with self._lock:
            if not self._started:
                return False
            if self._clients_healthy_locked():
                return True
            return self._reconnect_locked() and self._clients_healthy_locked()

    def close(self):
        with self._lock:
            if not self._started:
                return
            self._started = False
            self._monitor_stop.set()
            monitor = self._monitor_thread
            self._monitor_thread = None
            command_consumer, result_consumer = self.consumer, self.result_consumer
            self.consumer, self.result_consumer = None, None
        if monitor is not None and monitor is not threading.current_thread():
            monitor.join(timeout=2)
        _shutdown_client(command_consumer)
        _shutdown_client(result_consumer)
        self.publisher.close()
        if self.proposal_publisher is not None:
            self.proposal_publisher.close()

    @property
    def started(self) -> bool:
        """返回本地生命周期状态；readiness 使用 healthy 触发真实探测。"""
        with self._lock:
            return self._started

    @property
    def healthy(self) -> bool:
        """返回当前 Consumer 是否可通过 RocketMQ 路由探测。"""
        return self.ensure_healthy()
