import json
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))
from mq_runtime import RedisCommandInbox, RedisEventOutbox, RocketMqEventPublisher, _CommandListener
from rocketmq import ConsumeResult


class FakeRedis:
    def __init__(self):
        self.values = {}
        self.hashes = {}
        self.lists = {}
        self.sequence = 0

    def set(self, key, value, nx=False, ex=None):
        if nx and key in self.values:
            return False
        self.values[key] = value
        return True

    def get(self, key):
        return self.values.get(key)

    def incr(self, key):
        self.sequence += 1
        return self.sequence

    def hset(self, key, mapping):
        self.hashes[key] = mapping

    def rpush(self, key, value):
        self.lists.setdefault(key, []).append(value)

    def lrem(self, key, count, value):
        self.lists[key].remove(value)

    def delete(self, key):
        self.hashes.pop(key, None)


class FakeProducer:
    def __init__(self):
        self.messages = []

    def send(self, message):
        self.messages.append(message)
        return SimpleNamespace(message_id="mq-1")

    def shutdown(self):
        pass


class MqRuntimeTests(TestCase):
    def test_command_inbox_accepts_duplicate_and_rejects_hash_conflict(self):
        inbox = RedisCommandInbox(FakeRedis(), "test")
        command = {"dispatch_id": "d1", "request_hash": "sha256:1"}
        self.assertEqual("claimed", inbox.claim("d1", command["request_hash"], command))
        self.assertEqual("duplicate", inbox.claim("d1", command["request_hash"], command))
        with self.assertRaises(ValueError):
            inbox.claim("d1", "sha256:2", command)

    def test_listener_executes_only_after_inbox_claim(self):
        inbox = RedisCommandInbox(FakeRedis(), "test")
        executed = []
        listener = _CommandListener(inbox, executed.append)
        command = {"dispatch_id": "d1", "request_hash": "sha256:1"}
        message = SimpleNamespace(body=json.dumps(command).encode())
        self.assertEqual(ConsumeResult.SUCCESS, listener.consume(message))
        self.assertEqual(ConsumeResult.SUCCESS, listener.consume(message))
        self.assertEqual([command], executed)

    def test_event_outbox_is_acked_only_after_broker_send(self):
        redis_client = FakeRedis()
        outbox = RedisEventOutbox(redis_client, "test")
        producer = FakeProducer()
        publisher = RocketMqEventPublisher(producer, "foodmate-agent-event-v1", outbox)
        event = {
            "schema_version": "v1", "run_id": "r1", "dispatch_id": "d1",
            "event_id": "e1", "event_seq": 1,
        }
        publisher.publish(event)
        self.assertEqual(1, len(producer.messages))
        self.assertEqual([], redis_client.lists["test:outbox:event"])
