import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock

import runtime_env


class RuntimeEnvTests(TestCase):
    def test_project_env_resolves_references_without_overriding_process_values(self):
        with TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "PRIMARY=from-file\n"
                "DERIVED=${PRIMARY}\n"
                "EXPLICIT=${PROCESS_VALUE}\n",
                encoding="utf-8",
            )
            with mock.patch.object(runtime_env.Path, "resolve", return_value=Path(directory) / "agent-runtime" / "runtime_env.py"), mock.patch.dict(
                os.environ, {"PROCESS_VALUE": "from-process"}, clear=False
            ):
                os.environ.pop("PRIMARY", None)
                os.environ.pop("DERIVED", None)
                os.environ.pop("EXPLICIT", None)
                runtime_env.load_project_env()
                self.assertEqual("from-file", os.environ["PRIMARY"])
                self.assertEqual("from-file", os.environ["DERIVED"])
                self.assertEqual("from-process", os.environ["EXPLICIT"])
