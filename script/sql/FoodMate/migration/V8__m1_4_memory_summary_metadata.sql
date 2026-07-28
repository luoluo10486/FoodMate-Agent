-- M1-4 短期摘要与长期记忆候选的可审计元数据。

ALTER TABLE session_summaries
    ADD COLUMN IF NOT EXISTS covered_from_sequence INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS covered_to_sequence INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS source_message_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(64) NOT NULL DEFAULT 'foodmate-summary-deterministic-v1',
    ADD COLUMN IF NOT EXISTS content_digest VARCHAR(71),
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS invalidated_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_session_summary_range') THEN
        ALTER TABLE session_summaries ADD CONSTRAINT chk_session_summary_range CHECK (
            covered_from_sequence >= 0 AND covered_to_sequence >= covered_from_sequence AND source_message_count >= 0 AND version >= 1
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_session_summaries_coverage
    ON session_summaries(session_id, covered_to_sequence DESC)
    WHERE is_deleted = FALSE;
