package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.AdminManagementStore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminManagementMapper extends AdminManagementStore {
    @Update(
            "UPDATE users SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE user_id=#{userId} AND is_deleted=FALSE")
    int updateUserStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("operatorId") long operatorId);

    @Update(
            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE user_id=#{userId} AND revoked_at IS NULL AND is_deleted=FALSE")
    int revokeSessions(@Param("userId") long userId, @Param("operatorId") long operatorId);

    @Update(
            "UPDATE tool_registries SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE name=#{name} AND is_deleted=FALSE")
    int updateToolStatus(
            @Param("name") String name,
            @Param("status") String status,
            @Param("operatorId") long operatorId);

    @Update(
            "UPDATE knowledge_documents SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE document_id=#{documentId} AND is_deleted=FALSE")
    int updateKnowledgeStatus(
            @Param("documentId") long documentId,
            @Param("status") String status,
            @Param("operatorId") long operatorId);

    @Update(
            "<script><choose><when test=\"resourceType == 'user'\">UPDATE users SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE user_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType == 'knowledge_document'\">UPDATE knowledge_documents SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE document_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType == 'food_log'\">UPDATE food_logs SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE food_log_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType == 'meal_plan'\">UPDATE meal_plans SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE meal_plan_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType == 'message'\">UPDATE messages SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE message_id=#{resourceId} AND is_deleted=TRUE</when><otherwise>SELECT 0</otherwise></choose></script>")
    int restore(
            @Param("resourceType") String resourceType,
            @Param("resourceId") long resourceId,
            @Param("operatorId") long operatorId);

    @Select("SELECT COALESCE(MAX(operation_audit_id),0)+1 FROM operation_audits")
    long nextAuditId();

    @Insert(
            "INSERT INTO operation_audits(operation_audit_id,operator_id,trace_id,target_type,target_id,action,result,created_by,updated_by) VALUES (#{id},#{operatorId},#{traceId},#{targetType},#{targetId},#{action},'success',#{operatorId},#{operatorId})")
    void insertAudit(Audit audit);
}
