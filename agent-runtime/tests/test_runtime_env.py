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

    def test_project_env_does_not_load_sensitive_values_by_default(self):
        with TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "PUBLIC_SETTING=from-file\n"
                "FOODMATE_RAG_EMBEDDING_API_KEY=secret-from-file\n"
                "DB_PASSWORD=password-from-file\n"
                "RUNTIME_JAVA_PRIVATE_KEY=private-key-from-file\n",
                encoding="utf-8",
            )
            with mock.patch.object(
                runtime_env.Path,
                "resolve",
                return_value=Path(directory) / "agent-runtime" / "runtime_env.py",
            ), mock.patch.dict(os.environ, {}, clear=True):
                runtime_env.load_project_env()

                self.assertEqual("from-file", os.environ["PUBLIC_SETTING"])
                self.assertNotIn("FOODMATE_RAG_EMBEDDING_API_KEY", os.environ)
                self.assertNotIn("DB_PASSWORD", os.environ)
                self.assertNotIn("RUNTIME_JAVA_PRIVATE_KEY", os.environ)

    def test_project_env_loads_sensitive_values_only_with_process_opt_in(self):
        with TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "FOODMATE_RAG_EMBEDDING_API_KEY=secret-from-file\n",
                encoding="utf-8",
            )
            with mock.patch.object(
                runtime_env.Path,
                "resolve",
                return_value=Path(directory) / "agent-runtime" / "runtime_env.py",
            ), mock.patch.dict(
                os.environ,
                {"FOODMATE_RUNTIME_ALLOW_DOTENV_SECRETS": "true"},
                clear=True,
            ):
                runtime_env.load_project_env()

                self.assertEqual(
                    "secret-from-file", os.environ["FOODMATE_RAG_EMBEDDING_API_KEY"]
                )
