package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.AdminExportRepository.JobRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 受限管理员导出任务的 PostgreSQL 持久化映射。 */
@Mapper
public interface AdminExportMapper {
    @Insert(
            "INSERT INTO admin_export_jobs(admin_export_job_id,operator_id,resource,filters_json,fields_json,status,created_by,updated_by) VALUES (#{jobId},#{operatorId},#{resource},CAST(#{filtersJson} AS jsonb),CAST(#{fieldsJson} AS jsonb),'queued',#{operatorId},#{operatorId})")
    int insertJob(
            long jobId, long operatorId, String resource, String filtersJson, String fieldsJson);

    @Select(
            "SELECT admin_export_job_id AS jobId,operator_id AS operatorId,resource,filters_json::text AS filtersJson,fields_json::text AS fieldsJson,status,object_key AS objectKey,expires_at AS expiresAt,completed_at AS completedAt,download_consumed_at AS consumedAt,failure_code AS failureCode FROM admin_export_jobs WHERE admin_export_job_id=#{jobId} AND is_deleted=FALSE")
    JobRow find(long jobId);

    @Select(
            "SELECT admin_export_job_id FROM admin_export_jobs WHERE status='queued' AND is_deleted=FALSE ORDER BY created_at,admin_export_job_id LIMIT #{limit}")
    List<Long> queuedJobs(int limit);

    @Update(
            "UPDATE admin_export_jobs SET status='running',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE admin_export_job_id=#{jobId} AND status='queued' AND is_deleted=FALSE")
    int startJob(long jobId);

    @Update(
            "UPDATE admin_export_jobs SET status='completed',object_key=#{objectKey},completed_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+INTERVAL '24 hours',updated_at=CURRENT_TIMESTAMP WHERE admin_export_job_id=#{jobId} AND status='running' AND is_deleted=FALSE")
    int completeJob(long jobId, String objectKey);

    @Update(
            "UPDATE admin_export_jobs SET status='failed',failure_code=#{failureCode},updated_at=CURRENT_TIMESTAMP WHERE admin_export_job_id=#{jobId} AND status='running' AND is_deleted=FALSE")
    int failJob(long jobId, String failureCode);

    @Update(
            "UPDATE admin_export_jobs SET download_consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE admin_export_job_id=#{jobId} AND operator_id=#{operatorId} AND status='completed' AND download_consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP AND is_deleted=FALSE")
    int consumeJob(long operatorId, long jobId);

    @Select(
            "SELECT object_key FROM admin_export_jobs WHERE admin_export_job_id=#{jobId} AND is_deleted=FALSE")
    String objectKey(long jobId);
}
