package com.foodmate.infrastructure.persistence.conversation;

import com.foodmate.application.conversation.port.out.MemoryRepository.MemorySnapshot;
import com.foodmate.application.conversation.port.out.MemoryRepository.NewMemory;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 长期语义记忆 MyBatis 映射。 */
@Mapper
public interface MemoryMapper {
    @Select(
            """
            SELECT s.user_id
            FROM agent_runs r
            JOIN sessions s ON s.session_id = r.session_id
            WHERE r.agent_run_id = #{runId}
              AND r.is_deleted = FALSE
              AND s.is_deleted = FALSE
            """)
    Long findRunOwner(long runId);

    @Select(
            """
            SELECT EXISTS(
                SELECT 1 FROM user_memories
                WHERE user_id = #{userId}
                  AND memory_type = #{type}
                  AND memory_key = #{key}
                  AND is_deleted = FALSE
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                  AND memory_value::text <> CAST(#{valueJson} AS jsonb)::text
            )
            """)
    boolean hasDifferentValue(
            @Param("userId") long userId,
            @Param("type") String type,
            @Param("key") String key,
            @Param("valueJson") String valueJson);

    @Select(
            """
            <script>
            SELECT EXISTS(
                SELECT 1
                FROM user_memories memory
                WHERE memory.user_id = #{userId}
                  AND (memory.is_deleted = TRUE OR memory.suppressed_source_message_ids &lt;&gt; '[]'::jsonb)
                  AND EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements_text(
                          COALESCE(memory.source_message_ids, '[]'::jsonb)
                          || COALESCE(memory.suppressed_source_message_ids, '[]'::jsonb)
                      ) source_id
                      WHERE source_id.value IN
                      <foreach collection="sourceMessageIds" item="sourceId" open="(" separator="," close=")">
                          #{sourceId}
                      </foreach>
                  )
            )
            </script>
            """)
    boolean hasSuppressedSourceMessages(
            @Param("userId") long userId, @Param("sourceMessageIds") List<String> sourceMessageIds);

    @Insert(
            """
            INSERT INTO user_memories(
                memory_id, user_id, memory_type, memory_key, memory_value,
                confidence, source, scope, confirmation_status, expires_at,
                source_message_ids, suppressed_source_message_ids, created_by, updated_by
            ) VALUES (
                #{id}, #{userId}, #{type}, #{key}, CAST(#{valueJson} AS jsonb),
                #{confidence}, #{source}, #{scope}, #{confirmationStatus},
                CASE
                    WHEN #{type} IN ('plan', 'meal_plan', 'recipe_plan', 'weekly_recipe')
                        THEN CURRENT_TIMESTAMP + INTERVAL '7 days'
                    WHEN #{type} IN ('temporary', 'session_context')
                        THEN CURRENT_TIMESTAMP + INTERVAL '24 hours'
                    ELSE NULL
                END,
                CAST(#{sourceMessageIdsJson} AS jsonb), '[]'::jsonb,
                #{userId}, #{userId}
            )
            """)
    void insert(NewMemory memory);

    @Select(
            """
            SELECT memory.memory_id AS memoryId, memory.memory_type AS memoryType, memory.memory_key AS memoryKey,
                   memory.memory_value::text AS memoryValue, memory.confidence, memory.source, memory.scope,
                   memory.confirmation_status AS confirmationStatus, memory.expires_at AS expiresAt, memory.updated_at AS updatedAt
            FROM user_memories memory
            WHERE memory.user_id = #{userId}
              AND memory.is_deleted = FALSE
              AND (memory.expires_at IS NULL OR memory.expires_at > CURRENT_TIMESTAMP)
              AND NOT EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements_text(COALESCE(memory.source_message_ids, '[]'::jsonb)) source_id
                  JOIN messages message ON message.message_id::text = source_id.value
                  WHERE message.is_deleted = TRUE
              )
            ORDER BY memory.updated_at DESC
            LIMIT #{limit}
            """)
    List<MemorySnapshot> findVisible(@Param("userId") long userId, @Param("limit") int limit);

    @Select(
            """
            SELECT memory_id AS memoryId, memory_type AS memoryType, memory_key AS memoryKey,
                   memory_value::text AS memoryValue, confidence, source, scope,
                   confirmation_status AS confirmationStatus, expires_at AS expiresAt, updated_at AS updatedAt
            FROM user_memories
            WHERE memory_id = #{memoryId} AND user_id = #{userId} AND is_deleted = FALSE
            """)
    MemorySnapshot findOwned(@Param("userId") long userId, @Param("memoryId") long memoryId);

    @Select(
            """
            SELECT EXISTS(
                SELECT 1 FROM user_memories
                WHERE memory_id = #{memoryId} AND user_id = #{userId} AND is_deleted = FALSE
            )
            """)
    boolean existsOwned(@Param("userId") long userId, @Param("memoryId") long memoryId);

    @Update(
            """
            UPDATE user_memories
            SET memory_value = CAST(#{valueJson} AS jsonb),
                scope = COALESCE(NULLIF(#{scope}, ''), scope),
                suppressed_source_message_ids =
                    COALESCE(suppressed_source_message_ids, '[]'::jsonb)
                    || COALESCE(source_message_ids, '[]'::jsonb),
                source_message_ids = '[]'::jsonb,
                confirmation_status = 'confirmed', updated_at = CURRENT_TIMESTAMP, updated_by = #{userId}
            WHERE memory_id = #{memoryId} AND user_id = #{userId} AND is_deleted = FALSE
            """)
    int updateOwned(
            @Param("userId") long userId,
            @Param("memoryId") long memoryId,
            @Param("valueJson") String valueJson,
            @Param("scope") String scope);

    @Update(
            """
            UPDATE user_memories
            SET is_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, deleted_by = #{userId},
                updated_at = CURRENT_TIMESTAMP, updated_by = #{userId}
            WHERE memory_id = #{memoryId} AND user_id = #{userId} AND is_deleted = FALSE
            """)
    int softDeleteOwned(@Param("userId") long userId, @Param("memoryId") long memoryId);

    @Update(
            """
            UPDATE user_memories
            SET confirmation_status = 'confirmed', updated_at = CURRENT_TIMESTAMP, updated_by = #{userId}
            WHERE memory_id = #{memoryId} AND user_id = #{userId} AND is_deleted = FALSE
            """)
    int confirmOwned(@Param("userId") long userId, @Param("memoryId") long memoryId);
}
