package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.account.AdminDashboardStore;
import com.foodmate.application.account.AdminManagementStore;
import com.foodmate.application.runtime.persistence.AdmissionReconciliationStore;
import com.foodmate.application.runtime.persistence.CancellationStore;
import com.foodmate.application.runtime.persistence.DispatchOutboxStore;
import com.foodmate.application.runtime.persistence.DlqStore;
import com.foodmate.application.runtime.persistence.MemoryStore;
import com.foodmate.application.runtime.persistence.ProposalInboxStore;
import com.foodmate.application.runtime.persistence.ProtocolAuditStore;
import com.foodmate.application.runtime.persistence.SessionSummaryStore;
import com.foodmate.application.runtime.persistence.ToolGatewayStore;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 本地无数据库 stub 的显式持久化适配器。 仅用于 local-stub Profile；正式运行不会静默降级到该实现。 */
@Configuration
@Profile("local-stub")
public class LocalStubPersistenceConfig {
    @Bean
    SessionSummaryStore localSessionSummaryStore() {
        return new SessionSummaryStore() {
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
        };
    }

    @Bean
    MemoryStore localMemoryStore() {
        return new MemoryStore() {
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
    DispatchOutboxStore localDispatchOutboxStore() {
        return new DispatchOutboxStore() {
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
    CancellationStore localCancellationStore() {
        return new CancellationStore() {
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
    DlqStore localDlqStore() {
        return new DlqStore() {
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
    AdmissionReconciliationStore localAdmissionReconciliationStore() {
        return new AdmissionReconciliationStore() {
            public List<RunRef> findQueueExpired(int timeoutSeconds, int limit) {
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
    ProtocolAuditStore localProtocolAuditStore() {
        return (id, requestId, fingerprint, errorCode, envelopeJson) -> {};
    }

    @Bean
    ProposalInboxStore localProposalInboxStore() {
        return new ProposalInboxStore() {
            public int claim(String proposalId, String requestHash, String payload) {
                return 1;
            }

            public java.util.Map<String, Object> find(String proposalId) {
                return java.util.Map.of();
            }

            public int complete(String proposalId, String resultJson) {
                return 1;
            }
        };
    }

    @Bean
    AdminManagementStore localAdminManagementStore() {
        return new AdminManagementStore() {
            public int updateUserStatus(long userId, String status, long operatorId) {
                return 0;
            }

            public int revokeSessions(long userId, long operatorId) {
                return 0;
            }

            public int updateToolStatus(String name, String status, long operatorId) {
                return 0;
            }

            public int updateKnowledgeStatus(long documentId, String status, long operatorId) {
                return 0;
            }

            public int restore(String resourceType, long resourceId, long operatorId) {
                return 0;
            }

            public long nextAuditId() {
                return 1;
            }

            public void insertAudit(AdminManagementStore.Audit audit) {}
        };
    }

    @Bean
    AdminDashboardStore localAdminDashboardStore() {
        return new AdminDashboardStore() {
            public java.util.Map<String, Object> overview() {
                return java.util.Map.of("runs_today", 0, "failure_rate", 0);
            }

            public long modelUsageCount() {
                return 0;
            }

            public long knowledgeCount() {
                return 0;
            }

            public List<java.util.Map<String, Object>> runs() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> toolCalls() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> sqlAudits() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> tools() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> usage() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> knowledge() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> deleted() {
                return List.of();
            }

            public List<java.util.Map<String, Object>> operationAudits() {
                return List.of();
            }
        };
    }

    @Bean
    ToolGatewayStore localToolGatewayStore() {
        return new ToolGatewayStore() {
            public boolean runExists(long runId) {
                return false;
            }

            public List<java.util.Map<String, Object>> executeRead(String statement) {
                return List.of();
            }

            public void audit(Audit audit) {}
        };
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("persistence is disabled by the local-stub profile");
    }
}
