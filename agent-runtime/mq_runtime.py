"""RocketMQ/Redis transport for the local Python Agent Runtime.

The runtime never writes FoodMate business tables. Redis records technical
command idempotency, while Java remains the PostgreSQL business authority.
"""

import json
import os
import threading
from typing import Callable

import redis
from rocketmq import ClientConfiguration, ConsumeResult, Credentials, FilterExpression, Message, MessageListener, Producer, PushConsumer


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
        producer.startup()
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


class _CommandListener(MessageListener):
    def __init__(self, inbox: RedisCommandInbox, execute: Callable[[dict], None]):
        self.inbox = inbox
        self.execute = execute

    def consume(self, message):
        try:
            command = json.loads(message.body.decode("utf-8"))
            dispatch_id = command["dispatch_id"]
            request_hash = command["request_hash"]
            result = self.inbox.claim(dispatch_id, request_hash, command)
            if result == "claimed":
                self.execute(command)
            return ConsumeResult.SUCCESS
        except ValueError:
            # Contract/idempotency conflicts are deterministic and must not retry forever.
            return ConsumeResult.SUCCESS
        except Exception:
            # Redis or execution failures leave the message unacknowledged for RocketMQ retry/DLQ.
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

    def __init__(self, execute: Callable[[dict], None], inbox=None, publisher=None):
        endpoint = os.getenv("FOODMATE_ROCKETMQ_PROXY_ADDR", "localhost:8081")
        config = ClientConfiguration(endpoint, Credentials())
        self.publisher = publisher or RocketMqEventPublisher()
        self.inbox = inbox or RedisCommandInbox()
        self.topic = os.getenv("FOODMATE_ROCKETMQ_TOPIC_AGENT_COMMAND", "foodmate-agent-command-v1")
        self.consumer = PushConsumer(
            config,
            os.getenv("FOODMATE_ROCKETMQ_CONSUMER_GROUP_PYTHON_AGENT_COMMAND", "foodmate-python-agent-command-v1"),
            _CommandListener(self.inbox, execute),
            subscription={self.topic: FilterExpression("*")},
            consumption_thread_count=int(os.getenv("FOODMATE_AGENT_WORKER_CONCURRENCY", "1")),
        )
        self._started = False
        self._lock = threading.Lock()

    def start(self):
        with self._lock:
            if self._started:
                return
            self.consumer.startup()
            self._started = True

    def close(self):
        with self._lock:
            if not self._started:
                return
            self.consumer.shutdown()
            self.publisher.close()
            self._started = False
