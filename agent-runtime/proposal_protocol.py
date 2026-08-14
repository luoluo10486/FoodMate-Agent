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
    tool_name: str | None = None
    confirmation_ref: str | None = None
    input: dict[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        body = {
            "proposal_id": self.proposal_id,
            "run_id": self.run_id,
            "proposal_type": self.proposal_type,
            "schema_version": self.schema_version,
            "payload": self.payload,
            "requires_confirmation": self.requires_confirmation,
        }
        if self.tool_name is not None:
            body["tool_name"] = self.tool_name
        if self.confirmation_ref is not None:
            body["confirmation_ref"] = self.confirmation_ref
        if self.input is not None:
            body["input"] = self.input
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
    if proposal.proposal_type == "tool":
        if proposal.tool_name != "food_log_writer":
            raise ValueError("TOOL_NAME_NOT_ALLOWED")
        if not proposal.confirmation_ref:
            raise ValueError("TOOL_CONFIRMATION_REF_REQUIRED")
        if not isinstance(proposal.input, dict) or not proposal.input.get("items"):
            raise ValueError("TOOL_INPUT_INVALID")
        if not proposal.payload.get("idempotency_key"):
            raise ValueError("TOOL_IDEMPOTENCY_KEY_REQUIRED")
    if proposal.request_hash:
        canonical = {
            "proposal_id": proposal.proposal_id,
            "run_id": proposal.run_id,
            "proposal_type": proposal.proposal_type,
            "schema_version": proposal.schema_version,
            "payload": proposal.payload,
            "requires_confirmation": proposal.requires_confirmation,
        }
        if proposal.tool_name is not None:
            canonical["tool_name"] = proposal.tool_name
        if proposal.confirmation_ref is not None:
            canonical["confirmation_ref"] = proposal.confirmation_ref
        if proposal.input is not None:
            canonical["input"] = proposal.input
        if proposal.request_hash != _request_hash(canonical):
            raise ValueError("PROPOSAL_REQUEST_HASH_INVALID")
