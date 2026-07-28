"""M1-4 最小 Proposal 协议：Python 只描述意图，绝不执行工具或 SQL。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from typing import Any


@dataclass(frozen=True)
class Proposal:
    proposal_id: str
    run_id: str
    proposal_type: str
    schema_version: str
    payload: dict[str, Any]
    requires_confirmation: bool = True
    request_hash: str = ""

    def as_dict(self) -> dict[str, Any]:
        body = {
            "proposal_id": self.proposal_id,
            "run_id": self.run_id,
            "proposal_type": self.proposal_type,
            "schema_version": self.schema_version,
            "payload": self.payload,
            "requires_confirmation": self.requires_confirmation,
        }
        body["request_hash"] = self.request_hash or "sha256:" + hashlib.sha256(
            json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest()
        return body


def validate_proposal(proposal: Proposal) -> None:
    if proposal.proposal_type not in {"tool", "sql_read"}:
        raise ValueError("PROPOSAL_TYPE_NOT_ALLOWED")
    if proposal.proposal_type == "sql_read":
        statement = str(proposal.payload.get("statement", "")).strip().lower()
        if not statement.startswith("select") or any(token in statement for token in ("insert ", "update ", "delete ", "drop ", "alter ", ";")):
            raise ValueError("SQL_PROPOSAL_NOT_READ_ONLY")
    if not proposal.proposal_id or not proposal.run_id or len(proposal.proposal_id) > 128:
        raise ValueError("PROPOSAL_ID_REQUIRED")
