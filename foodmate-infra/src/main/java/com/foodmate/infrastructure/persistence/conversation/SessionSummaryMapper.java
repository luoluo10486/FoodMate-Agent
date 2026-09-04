package com.foodmate.infrastructure.persistence.conversation;

import com.foodmate.application.conversation.port.out.ConversationSummaryRepository.MessageSnapshot;
import com.foodmate.application.conversation.port.out.ConversationSummaryRepository.NewSummary;
import com.foodmate.application.conversation.port.out.ConversationSummaryRepository.SummarySnapshot;
import com.foodmate.application.conversation.port.out.ConversationSummaryRepository.UpdatedSummary;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 会话摘要 MyBatis 映射；行锁和 CAS 必须留在同一数据库事务中。 */
@Mapper
public interface SessionSummaryMapper {
    @Select(
            """
            SELECT EXISTS(
                SELECT 1 FROM sessions
                WHERE session_id = #{sessionId} AND user_id = #{userId} AND is_deleted = FALSE
            )
            """)
    boolean ownsSession(long userId, long sessionId);

    @Select(
            """
            SELECT message.message_id AS messageId, message.sequence_no AS sequence,
                   message.role, message.content
            FROM messages message
            JOIN sessions session ON session.session_id = message.session_id
            WHERE message.session_id = #{sessionId} AND message.is_deleted = FALSE
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_memories memory
                  WHERE memory.user_id = session.user_id
                    AND (
                        memory.is_deleted = TRUE
                        OR memory.suppressed_source_message_ids &lt;&gt; '[]'::jsonb
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM jsonb_array_elements_text(
                            COALESCE(memory.source_message_ids, '[]'::jsonb)
                            || COALESCE(memory.suppressed_source_message_ids, '[]'::jsonb)
                        ) source_id
                        WHERE source_id.value = message.message_id::text
                    )
              )
            ORDER BY sequence_no
            """)
    @Options(useCache = false)
    List<MessageSnapshot> findEffectiveMessages(long sessionId);

    @Select(
            """
            SELECT summary_id AS id, version, source_message_count AS sourceCount,
                   invalidated_at IS NOT NULL AS invalidated
            FROM session_summaries
            WHERE session_id = #{sessionId} AND is_deleted = FALSE
            FOR UPDATE
            """)
    @Options(useCache = false)
    SummarySnapshot lockSummary(long sessionId);

    @Insert(
            """
            INSERT INTO session_summaries(
                summary_id, session_id, summary_text, key_constraints,
                covered_from_sequence, covered_to_sequence, source_message_count,
                prompt_version, content_digest, version, created_by, updated_by
            ) VALUES (
                #{id}, #{sessionId}, #{text}, CAST(#{structuredJson} AS jsonb),
                #{coveredFrom}, #{coveredTo}, #{sourceCount},
                #{promptVersion}, #{digest}, 1, #{operatorId}, #{operatorId}
            )
            """)
    void insertSummary(NewSummary summary);

    @Update(
            """
            UPDATE session_summaries
            SET summary_text = #{text},
                key_constraints = CAST(#{structuredJson} AS jsonb),
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

    @Update(
            """
            UPDATE session_summaries summary
            SET invalidated_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = #{userId}
            FROM sessions session
            WHERE summary.session_id = session.session_id
              AND session.user_id = #{userId}
              AND summary.is_deleted = FALSE
              AND session.is_deleted = FALSE
            """)
    int invalidateForUser(long userId);
}
