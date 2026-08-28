"""Load local-only Runtime configuration without printing secret values."""

from __future__ import annotations

import os
import re
from pathlib import Path


_VARIABLE_REFERENCE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")


def load_project_env() -> None:
    """加载仓库根目录 .env，且不覆盖调用进程显式传入的环境变量。"""
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.is_file():
        return
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

    resolved: dict[str, str] = {}

    def resolve(key: str, resolving: set[str]) -> str:
        if key in os.environ:
            return os.environ[key]
        if key in resolved:
            return resolved[key]
        if key in resolving:
            raise ValueError(f"cyclic .env reference: {key}")
        raw_value = file_values.get(key, "")
        resolving.add(key)
        value = _VARIABLE_REFERENCE.sub(
            lambda match: resolve(match.group(1), resolving), raw_value
        )
        resolving.remove(key)
        resolved[key] = value
        return value

    for key in file_values:
        if key not in os.environ:
            os.environ[key] = resolve(key, set())
