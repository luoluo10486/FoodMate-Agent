import json
import sys
import unittest
import base64
from pathlib import Path
from unittest.mock import patch
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.append(str(Path(__file__).parents[1]))
import runtime_server


class RuntimeContractTests(unittest.TestCase):
    def setUp(self):
        runtime_server._cancelled.clear()
        runtime_server._dispatches.clear()

    def test_execute_emits_ordered_lifecycle(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        with patch.object(runtime_server, "emit", side_effect=lambda *args: events.append(args[3])):
            runtime_server.execute(command)
        self.assertEqual(["DISPATCHED", "RUNNING", "SUCCEEDED"], events)

    def test_cancel_is_observed_before_execution(self):
        events = []
        command = {"run_id": "1", "dispatch_id": "d1", "deadline_at": "x", "attempt": 1}
        runtime_server._cancelled.add("1")
        with patch.object(runtime_server, "emit", side_effect=lambda *args: events.append(args[3])):
            runtime_server.execute(command)
        self.assertEqual(["DISPATCHED", "CANCELED"], events)

    def test_service_jwt_round_trip(self):
        private_key = Ed25519PrivateKey.generate()
        private_der = private_key.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        public_der = private_key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        with patch.object(runtime_server, "PYTHON_PRIVATE_KEY", base64.b64encode(private_der).decode()), patch.object(runtime_server, "PYTHON_KID", "python-2026-01"):
            token = runtime_server._sign("foodmate-agent-runtime", "foodmate-control-plane", "runtime:event")
        with patch.object(runtime_server, "JAVA_PUBLIC_KEY", base64.b64encode(public_der).decode()), patch.object(runtime_server, "JWT_ENABLED", True):
            self.assertTrue(runtime_server._verify(token, "foodmate-agent-runtime", "foodmate-control-plane", "runtime:event"))
            self.assertFalse(runtime_server._verify(token, "foodmate-agent-runtime", "foodmate-control-plane", "runtime:cancel"))
