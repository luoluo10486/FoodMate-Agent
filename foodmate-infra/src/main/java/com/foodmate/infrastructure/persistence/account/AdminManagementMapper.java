package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.AdminManagementRepository.ResourceSnapshot;
import com.foodmate.application.account.port.out.AdminManagementRepository.ToolSnapshot;
import com.foodmate.application.account.port.out.AdminManagementRepository.UserSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 管理写操作的 PostgreSQL 状态更新；统一审计由 application 端口负责。 */
@Mapper
public interface AdminManagementMapper {
    @Select(
            "SELECT user_id AS userId,role,status,revision FROM users WHERE user_id=#{userId} AND"
                    + " is_deleted=FALSE")
    UserSnapshot findUser(@Param("userId") long userId);

    @Select(
            "SELECT name,risk_level AS riskLevel,status,revision FROM tool_registries WHERE"
                    + " name=#{name} AND is_deleted=FALSE")
    ToolSnapshot findTool(@Param("name") String name);

    @Select(
            "<script><choose><when test=\"resourceType == 'user'\">SELECT 'user' AS"
                    + " resourceType,user_id AS resourceId,revision FROM users WHERE"
                    + " user_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType =="
                    + " 'knowledge_document'\">SELECT 'knowledge_document' AS resourceType,document_id"
                    + " AS resourceId,revision FROM knowledge_documents WHERE document_id=#{resourceId}"
                    + " AND is_deleted=TRUE</when><when test=\"resourceType == 'food_log'\">SELECT"
                    + " 'food_log' AS resourceType,food_log_id AS resourceId,revision FROM food_logs"
                    + " WHERE food_log_id=#{resourceId} AND is_deleted=TRUE</when><when"
                    + " test=\"resourceType == 'meal_plan'\">SELECT 'meal_plan' AS"
                    + " resourceType,meal_plan_id AS resourceId,revision FROM meal_plans WHERE"
                    + " meal_plan_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType"
                    + " == 'message'\">SELECT 'message' AS resourceType,message_id AS"
                    + " resourceId,revision FROM messages WHERE message_id=#{resourceId} AND"
                    + " is_deleted=TRUE</when><otherwise>SELECT NULL::varchar AS"
                    + " resourceType,NULL::bigint AS resourceId,NULL::bigint AS revision WHERE"
                    + " FALSE</otherwise></choose></script>")
    ResourceSnapshot findResource(
            @Param("resourceType") String resourceType, @Param("resourceId") long resourceId);

    @Update(
            "UPDATE users SET"
                    + " status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE user_id=#{userId} AND is_deleted=FALSE AND revision=#{revision}")
    int updateUserStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Update(
            "UPDATE user_auth_sessions SET"
                    + " revoked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId}"
                    + " WHERE user_id=#{userId} AND revoked_at IS NULL AND is_deleted=FALSE")
    int revokeSessions(@Param("userId") long userId, @Param("operatorId") long operatorId);

    @Update(
            "UPDATE users SET"
                    + " updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1 WHERE"
                    + " user_id=#{userId} AND is_deleted=FALSE AND revision=#{revision}")
    int bumpUserRevision(
            @Param("userId") long userId,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Update(
            "UPDATE tool_registries SET"
                    + " status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE name=#{name} AND is_deleted=FALSE AND revision=#{revision}")
    int updateToolStatus(
            @Param("name") String name,
            @Param("status") String status,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Update(
            "<script><choose><when test=\"resourceType == 'user'\">UPDATE users SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE user_id=#{resourceId} AND is_deleted=TRUE AND"
                    + " revision=#{revision}</when><when test=\"resourceType =="
                    + " 'knowledge_document'\">UPDATE knowledge_documents SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,visibility='draft',updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE document_id=#{resourceId} AND is_deleted=TRUE AND"
                    + " revision=#{revision}</when><when test=\"resourceType == 'food_log'\">UPDATE"
                    + " food_logs SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE food_log_id=#{resourceId} AND is_deleted=TRUE AND"
                    + " revision=#{revision}</when><when test=\"resourceType == 'meal_plan'\">UPDATE"
                    + " meal_plans SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE meal_plan_id=#{resourceId} AND is_deleted=TRUE AND"
                    + " revision=#{revision}</when><when test=\"resourceType == 'message'\">UPDATE"
                    + " messages SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1"
                    + " WHERE message_id=#{resourceId} AND is_deleted=TRUE AND"
                    + " revision=#{revision}</when><otherwise>SELECT 0</otherwise></choose></script>")
    int restore(
            @Param("resourceType") String resourceType,
            @Param("resourceId") long resourceId,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);
}
