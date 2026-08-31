import json
import time
import sys
import base64
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))
from mq_runtime import RedisCheckpoint, RedisCommandInbox, RedisProposalOutbox, RedisResultInbox, RedisEventOutbox, RocketMqEventPublisher, RocketMqKnowledgePurgeResultPublisher, RocketMqKnowledgeResultPublisher, RocketMqProposalPublisher, _CommandListener, _ResultListener, _startup_client_with_timeout
from proposal_protocol import Proposal
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

    def eval(self, script, key_count, version_key, value_key, expected, payload, ttl):
        current = int(self.values.get(version_key, "0"))
        if expected and int(expected) != current:
            return 0
        next_version = current + 1
        self.values[version_key] = str(next_version)
        self.values[value_key] = payload
        return next_version


class FakeProducer:
    def __init__(self):
        self.messages = []

    def send(self, message):
        self.messages.append(message)
        return SimpleNamespace(message_id="mq-1")

    def shutdown(self):
        pass


class FakeConsumer:
    instances = []

    def __init__(self, _configuration, _group, _listener, subscription, **_kwargs):
        self.topic = next(iter(subscription))
        self.is_running = False
        self.route_available = True
        self.shutdown_count = 0
        type(self).instances.append(self)

    def startup(self):
        self.is_running = True

    def shutdown(self):
        self.shutdown_count += 1
        self.is_running = False

    def _Client__update_topic_route(self, _topic):
        return object() if self.route_available else None


class FakePublisher:
    def __init__(self):
        self.reconnect_count = 0
        self.close_count = 0

    def reconnect(self):
        self.reconnect_count += 1

    def close(self):
        self.close_count += 1


class MqRuntimeTests(TestCase):
    def test_runtime_rebuilds_clients_when_started_connection_fails_health_probe(self):
        FakeConsumer.instances = []
        event_publisher = FakePublisher()
        proposal_publisher = FakePublisher()
        with patch("mq_runtime.PushConsumer", FakeConsumer):
            from mq_runtime import RocketMqRuntime

            runtime = RocketMqRuntime(
                lambda _command: None,
                publisher=event_publisher,
                proposal_publisher=proposal_publisher,
            )
            runtime.start()
            stale_command = runtime.consumer
            stale_result = runtime.result_consumer
            stale_command.route_available = False
            stale_result.route_available = False

            self.assertTrue(runtime.ensure_healthy())
            self.assertEqual(4, len(FakeConsumer.instances))
            self.assertIsNot(stale_command, runtime.consumer)
            self.assertIsNot(stale_result, runtime.result_consumer)
            self.assertEqual(1, event_publisher.reconnect_count)
            self.assertEqual(1, proposal_publisher.reconnect_count)
            self.assertTrue(runtime.ensure_healthy())
            runtime.close()

    def test_rocketmq_startup_timeout_returns_explicit_failure(self):
        class HangingClient:
            def startup(self):
                time.sleep(1)

        started = time.monotonic()
        with self.assertRaisesRegex(RuntimeError, "RUNTIME_MQ_STARTUP_FAILED"):
            _startup_client_with_timeout(HangingClient(), "test", 0.01)
        self.assertLess(time.monotonic() - started, 0.5)

    def test_redis_checkpoint_uses_cas_and_round_trips_encrypted_value(self):
        client = FakeRedis()
        key = base64.urlsafe_b64encode(b"01234567890123456789012345678901").decode()
        checkpoint = RedisCheckpoint(client, "test-checkpoint", ttl_seconds=60, encryption_key=key)

        version = checkpoint.save("r:d", {"node": "planner"})
        self.assertEqual((version, {"node": "planner"}), checkpoint.load("r:d"))
        self.assertEqual(2, checkpoint.save("r:d", {"node": "composer"}, version))
        with self.assertRaisesRegex(RuntimeError, "CHECKPOINT_CAS_CONFLICT"):
            checkpoint.save("r:d", {"node": "stale"}, version)
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

    def test_result_listener_is_idempotent_and_rejects_hash_conflict(self):
        inbox = RedisResultInbox(FakeRedis(), "test")
        received = []
        listener = _ResultListener(inbox, received.append)
        result = {"proposal_id": "p1", "request_hash": "sha256:1", "status": "succeeded"}
        message = SimpleNamespace(body=json.dumps(result).encode())
        self.assertEqual(ConsumeResult.SUCCESS, listener.consume(message))
        self.assertEqual(ConsumeResult.SUCCESS, listener.consume(message))
        self.assertEqual([result], received)
        conflict = dict(result, request_hash="sha256:2")
        self.assertEqual(ConsumeResult.SUCCESS, listener.consume(SimpleNamespace(body=json.dumps(conflict).encode())))
        self.assertEqual([result], received)

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

    def test_proposal_publisher_persists_and_acknowledges_after_send(self):
        redis_client = FakeRedis()
        outbox = RedisProposalOutbox(redis_client, "test")
        producer = FakeProducer()
        publisher = RocketMqProposalPublisher(producer, "foodmate-agent-proposal-v1", outbox)
        proposal = Proposal("p1", "42", "sql_read", "v1", {"statement": "SELECT 1", "invocation_id": "inv-1"})
        publisher.publish(proposal)
        self.assertEqual(1, len(producer.messages))
        self.assertEqual([], redis_client.lists["test:outbox:proposal"])
        self.assertEqual("p1", producer.messages[0].properties["foodmate_proposal_id"])
        self.assertIn('"request_hash":"sha256:', producer.messages[0].body.decode())

    def test_knowledge_result_publishers_use_separate_topics_and_contracts(self):
        producer = FakeProducer()
        index_publisher = RocketMqKnowledgeResultPublisher(producer, "foodmate-knowledge-index-result-v1")
        purge_publisher = RocketMqKnowledgePurgeResultPublisher(producer, "foodmate-knowledge-purge-result-v1")

        index_publisher.publish({"item_id": "item-1", "status": "indexed"})
        purge_publisher.publish({"task_id": 91, "status": "succeeded"})

        self.assertEqual(2, len(producer.messages))
        self.assertEqual("foodmate-knowledge-index-result-v1", producer.messages[0].topic)
        self.assertEqual("KnowledgeIndexResult", producer.messages[0].properties["foodmate_message_type"])
        self.assertEqual({"item-1"}, producer.messages[0].keys)
        self.assertEqual("foodmate-knowledge-purge-result-v1", producer.messages[1].topic)
        self.assertEqual("KnowledgePurgeResult", producer.messages[1].properties["foodmate_message_type"])
        self.assertEqual({"91"}, producer.messages[1].keys)
