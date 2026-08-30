package com.foodmate.application.knowledge.port.out;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.math.BigDecimal;
import java.util.List;

/** 知识库用例使用的持久化端口，由基础设施层提供实现。 */
public interface KnowledgeRepository {
    /** 在索引开始前持久化初始文档事实。 */
    void insertDocument(long documentId, String title, String storageKey, long operatorId);

    /** 持久化来源和授权元数据，不保存文档正文。 */
    void updateDocumentSource(
            long documentId,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            long operatorId);

    /** 执行允许的文档生命周期转换。 */
    int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId);

    /** 持久化导入任务事实。 */
    void insertImportJob(ImportJob job);

    /** 查找操作者范围内用于幂等重放的已有导入任务。 */
    ImportJob findImportJob(long operatorId, String idempotencyKey);

    /** 持久化属于导入任务的一个文件条目。 */
    void insertImportItem(ImportItem item);

    /** 持久化由 Runtime 工作进程消费的已提交索引请求。 */
    void insertIndexOutbox(long outboxId, long itemId, String payload);

    /** 在权威存储中执行文档可见性转换。 */
    int updateVisibility(long documentId, String visibility, long operatorId);

    /** 读取生命周期命令使用的权威文档版本。 */
    DocumentView document(long documentId);

    /** 重新检查 PostgreSQL 权威可见性和版本门禁。 */
    default boolean isPublicPublished(long documentId, String version) {
        return false;
    }

    /** 持久化可重放的可见性投影请求。 */
    void insertVisibilityOutbox(long outboxId, long documentId, String payload);

    /** 读取当前可领取的待处理索引消息。 */
    List<OutboxRow> pendingIndexOutbox(int limit);

    /** 读取当前可领取的待处理可见性消息。 */
    List<OutboxRow> pendingVisibilityOutbox(int limit);

    /** 为有界发布租约领取一条索引消息。 */
    int leaseIndexOutbox(long outboxId, String owner);

    /** 为有界发布租约领取一条可见性消息。 */
    int leaseVisibilityOutbox(long outboxId, String owner);

    /** 记录租约持有者成功发布索引消息。 */
    void markIndexOutboxPublished(long outboxId, String owner);

    /** 记录租约持有者成功发布可见性消息。 */
    void markVisibilityOutboxPublished(long outboxId, String owner);

    /** 将失败的索引消息发布安排到重试。 */
    void retryIndexOutbox(long outboxId, String owner, String error);

    /** 将失败的可见性消息发布安排到重试。 */
    void retryVisibilityOutbox(long outboxId, String owner, String error);

    /** 幂等应用一条索引结果并更新批次读模型。 */
    void applyIndexResult(IndexResult result, String payloadHash);

    /** 在同一事务中替换当前版本的权威分块事实。 */
    default void replaceKnowledgeChunks(IndexResult result) {
        // Local stub persistence does not own a database chunk table.
    }

    /** 读取批次级进度。 */
    JobView job(long jobId);

    /** 读取批次的文件级进度。 */
    List<ItemView> jobItems(long jobId);

    /** 读取可恢复 SSE 游标之后的批次事件。 */
    List<JobEvent> jobEvents(long jobId, long afterEventId);

    /** 查找导入文件条目所属的批次。 */
    long jobIdForItem(long itemId);

    /** 持久化一个可重放的批次进度事件。 */
    void insertJobEvent(long eventId, long jobId, Long itemId, String eventType, String payload);

    /** 重置一个失败条目并创建下一条索引 Outbox 事实。 */
    int retryItem(long itemId, long jobId, long operatorId, long outboxId, String payload);

    /** 一条可发布的持久化消息。 */
    record OutboxRow(long outboxId, long itemOrDocumentId, String topic, String payload) {}

    /** 权威文档版本状态。 */
    record DocumentView(long documentId, String version, boolean currentVersion) {}

    record IndexResult(
            long itemId,
            long documentId,
            String version,
            String status,
            int chunkCount,
            String errorCode,
            String errorSummary,
            int attempt,
            long tokenCount,
            BigDecimal costAmount,
            String modelVersion,
            String providerTraceId,
            List<IndexChunk> chunks) {
        public IndexResult(
                long itemId,
                long documentId,
                String version,
                String status,
                int chunkCount,
                String errorCode,
                int attempt,
                long tokenCount,
                BigDecimal costAmount,
                String modelVersion) {
            this(
                    itemId,
                    documentId,
                    version,
                    status,
                    chunkCount,
                    errorCode,
                    null,
                    attempt,
                    tokenCount,
                    costAmount,
                    modelVersion,
                    null,
                    List.of());
        }

        public IndexResult(
                long itemId,
                long documentId,
                String version,
                String status,
                int chunkCount,
                String errorCode,
                int attempt,
                long tokenCount,
                BigDecimal costAmount,
                String modelVersion,
                List<IndexChunk> chunks) {
            this(
                    itemId,
                    documentId,
                    version,
                    status,
                    chunkCount,
                    errorCode,
                    null,
                    attempt,
                    tokenCount,
                    costAmount,
                    modelVersion,
                    null,
                    chunks);
        }

        public IndexResult(
                long itemId,
                long documentId,
                String version,
                String status,
                int chunkCount,
                String errorCode,
                String errorSummary,
                int attempt,
                long tokenCount,
                BigDecimal costAmount,
                String modelVersion,
                List<IndexChunk> chunks) {
            this(
                    itemId,
                    documentId,
                    version,
                    status,
                    chunkCount,
                    errorCode,
                    errorSummary,
                    attempt,
                    tokenCount,
                    costAmount,
                    modelVersion,
                    null,
                    chunks);
        }

        public IndexResult {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    /** Runtime 索引工作进程返回的安全、有界分块事实。 */
    record IndexChunk(int chunkNo, String embeddingId, String sectionPath, String text) {}

    /** 批次级进度投影。 */
    record JobView(long jobId, String status, int totalItems, int indexedItems, int failedItems) {}

    /** 文件级上传和索引进度投影。 */
    record ItemView(
            long itemId,
            long documentId,
            String filename,
            String uploadStatus,
            String indexStatus,
            int attempts,
            String errorCode) {}

    /** 可恢复批次进度事件。 */
    record JobEvent(long eventId, String eventType, String payload) {}

    /** 由 application 边界持有的已清理导入批次元数据。 */
    record ImportJob(
            long jobId,
            long operatorId,
            String idempotencyKey,
            String mode,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            String traceId) {}

    /** 已清理的导入文件元数据；文件内容保留在对象存储中。 */
    record ImportItem(
            long itemId,
            long jobId,
            long documentId,
            String filename,
            String contentType,
            long size) {}
}
