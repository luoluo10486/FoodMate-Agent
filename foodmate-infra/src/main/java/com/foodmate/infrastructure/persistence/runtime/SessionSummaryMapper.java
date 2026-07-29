package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.SessionSummaryStore;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 会话摘要 MyBatis 映射；行锁和 CAS 必须留在同一数据库事务中。 */
@Mapper
public interface SessionSummaryMapper extends SessionSummaryStore {
    @Override
    @Select(
            """
            SELECT EXISTS(
                SELECT 1 FROM sessions
                WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_deleted = FALSE
            )
            """)
    boolean ownsSession(long userId, long sessionId);

    @Override
    @Select(
            """
            SELECT sequence_no AS sequence, role, content
            FROM messages
            WHERE session_id = #{sessionId} AND is_deleted = FALSE
            ORDER BY sequence_no
            """)
    @Options(useCache = false)
    List<MessageSnapshot> findEffectiveMessages(long sessionId);

    @Override
    @Select(
            """
            SELECT summary_id AS id, version, source_message_count AS sourceCount
            FROM session_summaries
            WHERE session_id = #{sessionId} AND is_deleted = FALSE
            FOR UPDATE
            """)
    @Options(useCache = false)
    SummarySnapshot lockSummary(long sessionId);

    @Override
    @Insert(
            """
            INSERT INTO session_summaries(
                summary_id, session_id, summary_text, key_constraints,
                covered_from_sequence, covered_to_sequence, source_message_count,
                prompt_version, content_digest, version, created_by, updated_by
            ) VALUES (
                #{id}, #{sessionId}, #{text}, '{}'::jsonb,
                #{coveredFrom}, #{coveredTo}, #{sourceCount},
                #{promptVersion}, #{digest}, 1, #{operatorId}, #{operatorId}
            )
            """)
    void insertSummary(NewSummary summary);

    @Override
    @Update(
            """
            UPDATE session_summaries
            SET summary_text = #{text},
                covered_from_sequence = #{coveredFrom},
                covered_to_sequence = #{coveredTo},
                source_message_count = #{sourceCount},
                prompt_version = #{promptVersion},
                content_digest = #{digest},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = #{operatorId},
                invalidated_at = NULL
            WHERE summary_id = #{id}
              AND version = #{expectedVersion}
              AND is_deleted = FALSE
            """)
    int updateSummary(UpdatedSummary summary);

    @Override
    @Update(
            """
            UPDATE session_summaries
            SET invalidated_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = #{userId}
            WHERE session_id = #{sessionId}
              AND is_deleted = FALSE
              AND EXISTS (
                  SELECT 1 FROM sessions
                  WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_deleted = FALSE
              )
            """)
    int invalidate(long userId, long sessionId);
}
