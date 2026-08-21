package com.foodmate.infrastructure.persistence.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.application.account.port.out.AdminDashboardRepository;
import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.conversation.port.out.ConversationSummaryRepository;
import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.runtime.port.out.AdmissionReconciliationRepository;
import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.OutboxRepository;
import com.foodmate.application.runtime.port.out.ProtocolAuditRepository;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 本地无数据 stub 的显式持久化适配器。 */
@Configuration
@Profile("local-stub")
public class LocalStubPersistenceConfig {
    @Bean
    ConversationSummaryRepository localConversationSummaryRepository() {
        return new ConversationSummaryRepository() {
            public boolean ownsSession(long userId, long sessionId) {
                return false;
            }

            public List<MessageSnapshot> findEffectiveMessages(long sessionId) {
                return List.of();
            }

            public SummarySnapshot lockSummary(long sessionId) {
                return null;
            }

            public void insertSummary(NewSummary summary) {
                throw unavailable();
            }

            public int updateSummary(UpdatedSummary summary) {
                throw unavailable();
            }

            public int invalidate(long userId, long sessionId) {
                return 0;
            }

            public int invalidateForUser(long userId) {
                return 0;
            }
        };
    }

    @Bean
    MemoryRepository localMemoryRepository() {
        return new MemoryRepository() {
            public Long findRunOwner(long runId) {
                return null;
            }

            public boolean hasDifferentValue(
                    long userId, String type, String key, String valueJson) {
                return false;
            }

            public void insert(NewMemory memory) {
                throw unavailable();
            }

            public List<MemorySnapshot> findVisible(long userId, int limit) {
                return List.of();
            }

            public MemorySnapshot findOwned(long userId, long memoryId) {
                return null;
            }

            public boolean existsOwned(long userId, long memoryId) {
                return false;
            }

            public int updateOwned(long userId, long memoryId, String valueJson, String scope) {
                throw unavailable();
            }

            public int softDeleteOwned(long userId, long memoryId) {
                throw unavailable();
            }

            public int confirmOwned(long userId, long memoryId) {
                throw unavailable();
            }
        };
    }

    @Bean
    OutboxRepository localOutboxRepository() {
        return new OutboxRepository() {
            public List<OutboxSnapshot> findPending(int limit) {
                return List.of();
            }

            public int lease(long outboxId, String owner) {
                return 0;
            }

            public void markPublished(long outboxId, String topic, String messageId) {
                throw unavailable();
            }

            public void markDelivered(long outboxId) {
                throw unavailable();
            }

            public void markFailed(long outboxId, String error) {
                throw unavailable();
            }
        };
    }

    @Bean
    CancellationRepository localCancellationRepository() {
        return new CancellationRepository() {
            public ActiveDispatch findActiveDispatch(long runId) {
                return null;
            }

            public void insertRequested(NewCancellation cancellation) {
                throw unavailable();
            }

            public int incrementCancellationEpoch(long runId) {
                throw unavailable();
            }

            public List<PendingCancellation> findRequested(int limit) {
                return List.of();
            }

            public int markDispatched(long cancellationId, String transport, String messageId) {
                throw unavailable();
            }
        };
    }

    @Bean
    DeadLetterRepository localDeadLetterRepository() {
        return new DeadLetterRepository() {
            public void insert(DlqMessage message) {
                throw unavailable();
            }

            public List<DlqEntry> findPending(int limit) {
                return List.of();
            }

            public long inboxCount(long runId, String eventId) {
                return 0;
            }

            public List<String> findRunStatuses(long runId) {
                return List.of();
            }

            public int resolve(long dlqId, String state, String note) {
                throw unavailable();
            }
        };
    }

    @Bean
    AdmissionReconciliationRepository localAdmissionReconciliationRepository() {
        return new AdmissionReconciliationRepository() {
            public List<RunRef> findQueueExpired(int timeoutSeconds, int limit) {
                return List.of();
            }

            public List<RunRef> findQueued(int limit) {
                return List.of();
            }

            public List<RunRef> findExecutionExpired(int limit) {
                return List.of();
            }

            public int failRun(long agentRunId, String code, String resultJson) {
                throw unavailable();
            }

            public int expireDispatches(long agentRunId) {
                throw unavailable();
            }

            public int failOutboxes(long agentRunId, String code) {
                throw unavailable();
            }

            public Long nextSseSequence(long agentRunId) {
                throw unavailable();
            }

            public void insertFailedEvent(
                    long eventId,
                    long agentRunId,
                    String sseEventId,
                    long sequence,
                    String sourceKey,
                    String payload) {
                throw unavailable();
            }

            public void updateSseSequence(long agentRunId, long sequence) {
                throw unavailable();
            }

            public void promoteOutboxes(List<String> runIds) {
                throw unavailable();
            }
        };
    }

    @Bean
    ProtocolAuditRepository localProtocolAuditRepository() {
        return (id, requestId, fingerprint, errorCode, envelopeJson) -> {};
    }

    @Bean
    InboxRepository localInboxRepository() {
        return new InboxRepository() {
            public int claim(String proposalId, String requestHash, String payload) {
                return 1;
            }

            public InboxRecord find(String proposalId) {
                return null;
            }

            public int complete(String proposalId, String resultJson) {
                return 1;
            }
        };
    }

    @Bean
    AdminManagementRepository localAdminManagementRepository() {
        return new AdminManagementRepository() {
            public int updateUserStatus(
                    long userId,
                    com.foodmate.shared.account.enums.UserStatus status,
                    long operatorId) {
                return 0;
            }

            public int revokeSessions(long userId, long operatorId) {
                return 0;
            }

            public int updateToolStatus(
                    String name,
                    com.foodmate.shared.runtime.enums.ToolStatus status,
                    long operatorId) {
                return 0;
            }

            public int restore(
                    com.foodmate.shared.admin.enums.RestorableResourceType resourceType,
                    long resourceId,
                    long operatorId) {
                return 0;
            }

            public long nextAuditId() {
                return 1;
            }

            public void insertAudit(AdminManagementRepository.Audit audit) {}
        };
    }

    @Bean
    KnowledgeRepository localKnowledgeRepository() {
        return new KnowledgeRepository() {
            public void insertDocument(
                    long documentId, String title, String storageKey, long operatorId) {}

            public void updateDocumentSource(
                    long documentId,
                    String sourceType,
                    String sourceName,
                    String sourceVersion,
                    String licenseNotice,
                    long operatorId) {}

            public int updateStatus(
                    long documentId,
                    com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus status,
                    long operatorId) {
                return 0;
            }

            public long nextAuditId() {
                return 1;
            }

            public void insertAudit(Audit audit) {}

            public void insertImportJob(ImportJob job) {}

            public void insertImportItem(ImportItem item) {}

            public void insertIndexOutbox(long outboxId, long itemId, String payload) {}

            public int updateVisibility(long documentId, String visibility, long operatorId) {
                return 0;
            }

            public void insertVisibilityOutbox(long outboxId, long documentId, String payload) {}

            public java.util.List<KnowledgeRepository.OutboxRow> pendingIndexOutbox(int limit) {
                return java.util.List.of();
            }

            public java.util.List<KnowledgeRepository.OutboxRow> pendingVisibilityOutbox(
                    int limit) {
                return java.util.List.of();
            }

            public int leaseIndexOutbox(long id, String owner) {
                return 0;
            }

            public int leaseVisibilityOutbox(long id, String owner) {
                return 0;
            }

            public void markIndexOutboxPublished(long id) {}

            public void markVisibilityOutboxPublished(long id) {}

            public void retryIndexOutbox(long id, String error) {}

            public void retryVisibilityOutbox(long id, String error) {}

            public void applyIndexResult(KnowledgeRepository.IndexResult result, String hash) {}

            public KnowledgeRepository.JobView job(long id) {
                return null;
            }

            public java.util.List<KnowledgeRepository.ItemView> jobItems(long id) {
                return java.util.List.of();
            }

            public java.util.List<KnowledgeRepository.JobEvent> jobEvents(long id, long after) {
                return java.util.List.of();
            }

            public int retryItem(long itemId, long operatorId, long outboxId, String payload) {
                return 0;
            }
        };
    }

    @Bean
    AdminDashboardRepository localAdminDashboardRepository() {
        return new AdminDashboardRepository() {
            public Overview overview() {
                return new Overview(0, BigDecimal.ZERO);
            }

            public long modelUsageCount() {
                return 0;
            }

            public long knowledgeCount() {
                return 0;
            }

            public List<RunRow> runs() {
                return List.of();
            }

            public List<ToolCallRow> toolCalls() {
                return List.of();
            }

            public List<SqlAuditRow> sqlAudits() {
                return List.of();
            }

            public List<ToolRow> tools() {
                return List.of();
            }

            public List<UsageRow> usage() {
                return List.of();
            }

            public List<KnowledgeRow> knowledge() {
                return List.of();
            }

            public List<DeletedRow> deleted() {
                return List.of();
            }

            public List<OperationAuditRow> operationAudits() {
                return List.of();
            }
        };
    }

    @Bean
    ToolGatewayPort localToolGatewayPort() {
        return new ToolGatewayPort() {
            public boolean runExists(long runId) {
                return false;
            }

            public RunContext runContext(long runId) {
                return null;
            }

            public List<JsonNode> executeRead(String statement) {
                return List.of();
            }

            public void audit(Audit audit) {}
        };
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("persistence is disabled by the local-stub profile");
    }
}
