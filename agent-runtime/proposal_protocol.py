"""M1-4 最小 Proposal 协议：Python 只描述意图，绝不执行工具或 SQL。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from typing import Any


MAX_ID_LENGTH = 128
MAX_STATEMENT_LENGTH = 8_192


def _request_hash(body: dict[str, Any]) -> str:
    return "sha256:" + hashlib.sha256(
        json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


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
        body["request_hash"] = self.request_hash or _request_hash(body)
        return body


def validate_proposal(proposal: Proposal) -> None:
    if proposal.schema_version != "v1":
        raise ValueError("PROPOSAL_SCHEMA_VERSION_INVALID")
    if proposal.proposal_type not in {"tool", "sql_read"}:
        raise ValueError("PROPOSAL_TYPE_NOT_ALLOWED")
    if not proposal.proposal_id or not proposal.run_id or len(proposal.proposal_id) > MAX_ID_LENGTH or len(proposal.run_id) > MAX_ID_LENGTH:
        raise ValueError("PROPOSAL_ID_REQUIRED")
    invocation_id = str(proposal.payload.get("invocation_id", ""))
    if not invocation_id or len(invocation_id) > MAX_ID_LENGTH:
        raise ValueError("PROPOSAL_INVOCATION_ID_REQUIRED")
    if proposal.proposal_type == "sql_read":
        statement = str(proposal.payload.get("statement", "")).strip().lower()
        if len(statement) > MAX_STATEMENT_LENGTH:
            raise ValueError("SQL_PROPOSAL_TOO_LARGE")
        if not statement.startswith("select") or any(token in statement for token in ("insert ", "update ", "delete ", "drop ", "alter ", ";")):
            raise ValueError("SQL_PROPOSAL_NOT_READ_ONLY")
    if proposal.request_hash:
        canonical = {
            "proposal_id": proposal.proposal_id,
            "run_id": proposal.run_id,
            "proposal_type": proposal.proposal_type,
            "schema_version": proposal.schema_version,
            "payload": proposal.payload,
            "requires_confirmation": proposal.requires_confirmation,
        }
        if proposal.request_hash != _request_hash(canonical):
            raise ValueError("PROPOSAL_REQUEST_HASH_INVALID")
