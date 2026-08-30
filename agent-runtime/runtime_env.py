"""Load local-only Runtime configuration without printing secret values."""

from __future__ import annotations

import os
import re
from pathlib import Path


_VARIABLE_REFERENCE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")
_SENSITIVE_VARIABLE = re.compile(
    r"(?i)(^|_)(API_KEY|PASSWORD|SECRET|TOKEN|PRIVATE_KEY|CLIENT_SECRET)(_|$)"
)
_ALLOW_DOTENV_SECRETS = "FOODMATE_RUNTIME_ALLOW_DOTENV_SECRETS"


def load_project_env() -> None:
    """加载仓库根目录 .env，且默认不把敏感值从文件带入进程。"""
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.is_file():
        return
    # 该开关必须来自调用进程，不能由 .env 自身开启，避免文件中的密钥
    # 在未明确授权时进入 Runtime 进程。
    allow_dotenv_secrets = (
        os.environ.get(_ALLOW_DOTENV_SECRETS, "").strip().lower() == "true"
    )
    file_values: dict[str, str] = {}
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key:
            file_values[key] = value

    loadable_values = {
        key: value
        for key, value in file_values.items()
        if allow_dotenv_secrets or not _SENSITIVE_VARIABLE.search(key)
    }

    resolved: dict[str, str] = {}

    def resolve(key: str, resolving: set[str]) -> str:
        if key in os.environ:
            return os.environ[key]
        if key in resolved:
            return resolved[key]
        if key in resolving:
            raise ValueError(f"cyclic .env reference: {key}")
        raw_value = loadable_values.get(key, "")
        resolving.add(key)
        value = _VARIABLE_REFERENCE.sub(
            lambda match: resolve(match.group(1), resolving), raw_value
        )
        resolving.remove(key)
        resolved[key] = value
        return value

    for key in loadable_values:
        if key not in os.environ:
            os.environ[key] = resolve(key, set())
