-- Manual rollback precondition for V26.
-- This file intentionally does not drop data. Export/retention approval is required before any rollback.
SELECT COUNT(*) AS feedback_rows FROM agent_feedback WHERE is_deleted = FALSE;
SELECT COUNT(*) AS high_risk_feedback_rows
FROM agent_feedback
WHERE is_deleted = FALSE AND high_risk = TRUE;
-- After an approved retention decision, an operator may remove the table with an explicit change ticket.
