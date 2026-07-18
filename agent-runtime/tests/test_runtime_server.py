import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

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

