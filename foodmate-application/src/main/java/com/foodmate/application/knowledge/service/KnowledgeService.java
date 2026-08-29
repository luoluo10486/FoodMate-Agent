package com.foodmate.application.knowledge.service;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import java.util.List;

/** 知识文档管理用例。 */
public interface KnowledgeService {
    /** 接收一个兼容旧接口的单文件上传并启动异步索引。 */
    long upload(
            long operatorId,
            String filename,
            String contentType,
            long size,
            InputStream input,
            String traceId);

    /** 为已授权管理员执行文档状态转换。 */
    void updateStatus(
            long documentId, KnowledgeDocumentStatus status, long operatorId, String traceId);

    /** 创建一个公共知识导入批次；索引和发布仍由后续显式步骤完成。 */
    long uploadBatch(long operatorId, ImportBatch batch, String traceId);

    /** 执行公共可见性转换并发出可重放的投影事实。 */
    void changeVisibility(long documentId, String visibility, long operatorId, String traceId);

    /** 读取当前批次进度及其文件条目。 */
    BatchDetail batch(long batchId);

    /** 读取给定 SSE 游标之后的批次进度事件。 */
    List<BatchEvent> batchEvents(long batchId, long afterEventId);

    /** 在管理员操作下重新排队一个失败条目。 */
    void retryItem(long batchId, long documentId, long operatorId, String traceId);

    record ImportBatch(
            String idempotencyKey,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            List<ImportFile> files) {}

    /** 提供给批次用例的单个已清理上传分片。 */
    record ImportFile(String filename, String contentType, long size, InputStream input) {}

    /** 管理 API 返回的批次进度视图。 */
    record BatchDetail(KnowledgeRepository.JobView job, List<KnowledgeRepository.ItemView> items) {}

    /** 管理 API 返回的单个可恢复批次事件。 */
    record BatchEvent(long eventId, String eventType, String payload) {}
}
