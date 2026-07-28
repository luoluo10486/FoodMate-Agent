"""Load local-only Runtime configuration without printing secret values."""

from __future__ import annotations

import os
from pathlib import Path


def load_project_env() -> None:
    """加载仓库根目录 .env，且不覆盖调用进程显式传入的环境变量。"""
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.is_file():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value
