package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetInsert;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.GovernanceState;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceInsert;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.ProviderRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteUpdate;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.UsageAggregate;
import com.foodmate.application.runtime.port.out.ModelSecretStatusPort;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 基于 PostgreSQL 的模型治理操作实现，不接收或返回供应商密钥。 */
@Service
public class ModelGovernanceAdminServiceImpl implements ModelGovernanceAdminService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final ModelGovernanceAdminRepository store;
    private final ModelSecretStatusPort secrets;
    private final OperationAuditService audit;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public ModelGovernanceAdminServiceImpl(
            ModelGovernanceAdminRepository store,
            ObjectProvider<ModelSecretStatusPort> secrets,
            OperationAuditService audit,
            IdGenerator ids,
            ObjectMapper mapper) {
        this.store = Objects.requireNonNull(store);
        this.secrets = secrets == null ? null : secrets.getIfAvailable();
        this.audit = Objects.requireNonNull(audit);
        this.ids = Objects.requireNonNull(ids);
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    @Override
    @Transactional(readOnly = true)
    public GovernanceView view(ModelGovernanceAdminService.UsageQuery query) {
        GovernanceState state =
                store.state(
                        query == null
                                ? new ModelGovernanceAdminRepository.UsageQuery(null, null)
                                : new ModelGovernanceAdminRepository.UsageQuery(
                                        query.from(), query.to()));
        return new GovernanceView(
                state.providers().stream().map(this::providerView).toList(),
                state.models().stream().map(ModelGovernanceAdminServiceImpl::modelView).toList(),
                state.routes().stream().map(ModelGovernanceAdminServiceImpl::routeView).toList(),
                state.prices(),
                state.budgets(),
                state.usage().stream().map(ModelGovernanceAdminServiceImpl::usageView).toList());
    }

    @Override
    @Transactional
    public MutationResult updateProviderStatus(
            String providerCode, String status, AdminWriteCommand command) {
        String target = clean(providerCode);
        String value = clean(status);
        String action = "model.provider.status.update";
        String digest = digest(action, target, value, command);
        boolean reserved = false;
        try {
            validate(command);
            requireSuperadmin(command);
            requireStatus(value);
            requireConfirmation(command, action, target, value);
            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            ProviderRow current = requireProvider(target, command.revision());
            reserve(command, "model_provider", target, action, digest, Map.of("status", value));
            reserved = true;
            if (store.updateProviderStatus(target, value, command.operatorId(), command.revision())
                    != 1) throw conflict("供应商状态已变化");
            MutationResult result =
                    new MutationResult(true, current.providerId(), value, command.revision() + 1);
            audit.complete(command.operatorId(), command.idempotencyKey(), resultJson(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, command, "model_provider", target, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public MutationResult updateModelStatus(
            long modelId, String status, AdminWriteCommand command) {
        String target = Long.toString(modelId);
        String value = clean(status);
        String action = "model.catalog.status.update";
        String digest = digest(action, target, value, command);
        boolean reserved = false;
        try {
            validate(command);
            requireSuperadmin(command);
            requirePositive(modelId, "model id");
            requireStatus(value);
            requireConfirmation(command, action, target, value);
            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            ModelGovernanceAdminRepository.ModelRow current = requireModel(modelId);
            requireRevision(current.revision(), command.revision(), "模型版本已变化");
            reserve(command, "model_catalog", target, action, digest, Map.of("status", value));
            reserved = true;
            if (store.updateModelStatus(modelId, value, command.operatorId(), command.revision())
                    != 1) throw conflict("模型状态已变化");
            MutationResult result =
                    new MutationResult(true, modelId, value, command.revision() + 1);
            audit.complete(command.operatorId(), command.idempotencyKey(), resultJson(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, command, "model_catalog", target, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public MutationResult updateRoute(RouteCommand command, AdminWriteCommand write) {
        String target = Long.toString(command == null ? 0 : command.routeId());
        String action = "model.route.update";
        String digest = digest(action, target, routeValue(command), write);
        boolean reserved = false;
        try {
            validate(write);
            requireSuperadmin(write);
            validateRoute(command);
            requireConfirmation(write, action, target, command.routeVersion());
            IdempotencyRecord previous = existing(write, digest);
            if (previous != null) return replay(previous, digest);
            RouteRow current = requireRoute(command.routeId());
            requireRevision(current.revision(), write.revision(), "模型路由版本已变化");
            reserve(
                    write,
                    "model_route_rule",
                    target,
                    action,
                    digest,
                    Map.of("route_version", command.routeVersion()));
            reserved = true;
            RouteUpdate update =
                    new RouteUpdate(
                            command.routeId(),
                            command.providerCode(),
                            command.modelName(),
                            command.fallbackProviderCode(),
                            command.fallbackModelName(),
                            command.priority(),
                            command.routeVersion(),
                            command.priceVersion(),
                            command.budgetPolicyVersion(),
                            command.maxCost(),
                            command.maxLatencyMs(),
                            command.status());
            if (store.updateRoute(update, write.operatorId(), write.revision()) != 1)
                throw conflict("模型路由状态已变化");
            MutationResult result =
                    new MutationResult(
                            true, command.routeId(), command.routeVersion(), write.revision() + 1);
            audit.complete(write.operatorId(), write.idempotencyKey(), resultJson(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, write, "model_route_rule", target, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public MutationResult createPrice(PriceCommand command, AdminWriteCommand write) {
        String target =
                command == null
                        ? "price"
                        : clean(command.providerCode())
                                + ":"
                                + clean(command.modelName())
                                + ":"
                                + clean(command.priceVersion());
        String action = "model.price.create";
        String digest = digest(action, target, priceValue(command), write);
        boolean reserved = false;
        try {
            validate(write);
            requireSuperadmin(write);
            validatePrice(command);
            requireConfirmation(write, action, target, command.priceVersion());
            IdempotencyRecord previous = existing(write, digest);
            if (previous != null) return replay(previous, digest);
            if (store.price(command.providerCode(), command.modelName(), command.priceVersion())
                    != null) throw conflict("价格版本已存在");
            long id = ids.nextId();
            reserve(
                    write,
                    "model_price_version",
                    target,
                    action,
                    digest,
                    Map.of("price_version", command.priceVersion()));
            reserved = true;
            store.insertPrice(
                    new PriceInsert(
                            id,
                            command.providerCode(),
                            command.modelName(),
                            command.priceVersion(),
                            command.inputPricePerMillion(),
                            command.outputPricePerMillion(),
                            command.currency(),
                            command.effectiveAt(),
                            write.operatorId()),
                    write.operatorId());
            MutationResult result = new MutationResult(true, id, command.priceVersion(), 1);
            audit.complete(write.operatorId(), write.idempotencyKey(), resultJson(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, write, "model_price_version", target, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public MutationResult createBudget(BudgetCommand command, AdminWriteCommand write) {
        String target =
                command == null
                        ? "budget"
                        : clean(command.policyKey()) + ":" + clean(command.policyVersion());
        String action = "model.budget.create";
        String digest = digest(action, target, budgetValue(command), write);
        boolean reserved = false;
        try {
            validate(write);
            requireSuperadmin(write);
            validateBudget(command);
            requireConfirmation(write, action, target, command.policyVersion());
            IdempotencyRecord previous = existing(write, digest);
            if (previous != null) return replay(previous, digest);
            if (store.budget(command.policyKey(), command.policyVersion()) != null)
                throw conflict("预算策略版本已存在");
            long id = ids.nextId();
            reserve(
                    write,
                    "model_budget_policy",
                    target,
                    action,
                    digest,
                    Map.of("policy_version", command.policyVersion()));
            reserved = true;
            store.insertBudget(
                    new BudgetInsert(
                            id,
                            command.policyKey(),
                            command.scene(),
                            command.scopeType(),
                            command.maxTotalTokens(),
                            command.maxCostCny(),
                            command.maxModelCalls(),
                            command.maxStepRetries(),
                            command.windowType(),
                            command.policyVersion(),
                            write.operatorId()),
                    write.operatorId());
            MutationResult result = new MutationResult(true, id, command.policyVersion(), 1);
            audit.complete(write.operatorId(), write.idempotencyKey(), resultJson(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, write, "model_budget_policy", target, action, digest, exception);
            throw exception;
        }
    }

    private ProviderView providerView(ProviderRow row) {
        ModelSecretStatusPort.SecretStatus secret =
                secrets == null
                        ? new ModelSecretStatusPort.SecretStatus(false, null)
                        : secrets.status(row.providerCode());
        return new ProviderView(
                row.providerId(),
                row.providerCode(),
                row.displayName(),
                row.status(),
                row.endpointConfigKey(),
                secret != null && secret.configured(),
                secret == null ? null : secret.fingerprint(),
                row.revision());
    }

    private static ModelView modelView(ModelGovernanceAdminRepository.ModelRow row) {
        return new ModelView(
                row.modelId(),
                row.providerCode(),
                row.modelName(),
                row.modelType(),
                row.status(),
                row.contextTokens(),
                row.maxOutputTokens(),
                row.timeoutMs(),
                row.revision());
    }

    private static RouteView routeView(RouteRow row) {
        return new RouteView(
                row.routeId(),
                row.tenantId(),
                row.scene(),
                row.modelType(),
                row.providerCode(),
                row.modelName(),
                row.fallbackProviderCode(),
                row.fallbackModelName(),
                row.priority(),
                row.routeVersion(),
                row.priceVersion(),
                row.budgetPolicyVersion(),
                row.maxCost(),
                row.maxLatencyMs(),
                row.status(),
                row.revision());
    }

    private static UsageView usageView(UsageAggregate row) {
        return new UsageView(
                row.providerCode(),
                row.modelName(),
                row.scene(),
                row.status(),
                row.calls(),
                row.totalTokens(),
                row.totalCost(),
                row.averageLatencyMs(),
                row.firstSeenAt(),
                row.lastSeenAt());
    }

    private ProviderRow requireProvider(String code, long revision) {
        ProviderRow value = store.provider(code);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        requireRevision(value.revision(), revision, "供应商版本已变化");
        return value;
    }

    private ModelGovernanceAdminRepository.ModelRow requireModel(long id) {
        ModelGovernanceAdminRepository.ModelRow value = store.model(id);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在");
        return value;
    }

    private RouteRow requireRoute(long id) {
        RouteRow value = store.route(id);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模型路由不存在");
        return value;
    }

    private void reserve(
            AdminWriteCommand command,
            String type,
            String id,
            String action,
            String digest,
            Map<String, ?> metadata) {
        if (audit.reserve(
                        command.operatorId(),
                        type,
                        id,
                        action,
                        digest,
                        command.idempotencyKey(),
                        metadata)
                != 1) throw conflict("幂等请求无法占用");
    }

    private IdempotencyRecord existing(AdminWriteCommand command, String digest) {
        IdempotencyRecord previous =
                audit.findIdempotency(command.operatorId(), command.idempotencyKey());
        if (previous != null && !digest.equals(previous.parametersDigest()))
            throw conflict("幂等键对应的请求参数已变化");
        return previous;
    }

    private MutationResult replay(IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest())) throw conflict("幂等键对应的请求参数已变化");
        if (!"success".equalsIgnoreCase(previous.result())) throw conflict("幂等请求正在处理中或已失败");
        try {
            JsonNode result = mapper.readTree(previous.responseJson());
            return new MutationResult(
                    result.path("changed").asBoolean(true),
                    result.path("resourceId").asLong(),
                    result.path("version").asText(null),
                    result.path("revision").asLong());
        } catch (JsonProcessingException exception) {
            throw conflict("幂等结果摘要无效");
        }
    }

    private void recordFailureIfNeeded(
            boolean reserved,
            AdminWriteCommand command,
            String type,
            String target,
            String action,
            String digest,
            RuntimeException exception) {
        if (command == null
                || command.operatorId() <= 0
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) return;
        String code =
                exception instanceof BusinessException business
                        ? business.errorCode().code()
                        : ErrorCode.INTERNAL_ERROR.code();
        if (!reserved) {
            if (audit.findIdempotency(command.operatorId(), command.idempotencyKey()) == null)
                audit.recordFailure(
                        command.operatorId(),
                        type,
                        target,
                        action,
                        "failed",
                        code,
                        digest,
                        command.idempotencyKey(),
                        Map.of("failure", "model_governance"));
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            audit.recordFailure(
                    command.operatorId(),
                    type,
                    target,
                    action,
                    "failed",
                    code,
                    digest,
                    command.idempotencyKey(),
                    Map.of("failure", "model_governance"));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK)
                            audit.recordFailure(
                                    command.operatorId(),
                                    type,
                                    target,
                                    action,
                                    "failed",
                                    code,
                                    digest,
                                    command.idempotencyKey(),
                                    Map.of("failure", "model_governance"));
                    }
                });
    }

    private static void validate(AdminWriteCommand command) {
        if (command == null || command.operatorId() <= 0 || command.operatorRole() == null)
            throw invalid("管理操作上下文无效");
        if (command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw invalid("Idempotency-Key 必须为 1-128 个字符");
        if (command.revision() < 1) throw invalid("revision 必须大于 0");
    }

    private static void requireSuperadmin(AdminWriteCommand command) {
        if (command.operatorRole() != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN, "模型治理仅允许 superadmin 操作");
    }

    private static void requireConfirmation(
            AdminWriteCommand command, String action, String target, String value) {
        String expected = confirmationDigest(action, target, value, command.revision());
        if (!command.confirmed()
                || command.confirmationDigest() == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        command.confirmationDigest().getBytes(StandardCharsets.UTF_8)))
            throw conflict("需要匹配的确认摘要");
    }

    private static void requireRevision(long actual, long expected, String message) {
        if (actual != expected) throw conflict(message);
    }

    private static void validateRoute(RouteCommand value) {
        if (value == null
                || value.routeId() <= 0
                || blank(value.providerCode())
                || blank(value.modelName())
                || blank(value.routeVersion())
                || blank(value.priceVersion())
                || blank(value.budgetPolicyVersion())
                || value.priority() < 0
                || !validStatus(value.status())) throw invalid("模型路由参数无效");
        if (value.maxCost() != null && value.maxCost().signum() < 0) throw invalid("路由成本上限不能为负");
        if (value.maxLatencyMs() != null && value.maxLatencyMs() <= 0) throw invalid("路由延迟上限必须为正");
    }

    private static void validatePrice(PriceCommand value) {
        if (value == null
                || blank(value.providerCode())
                || blank(value.modelName())
                || blank(value.priceVersion())
                || blank(value.currency())
                || value.effectiveAt() == null
                || value.inputPricePerMillion() == null
                || value.outputPricePerMillion() == null
                || value.inputPricePerMillion().signum() < 0
                || value.outputPricePerMillion().signum() < 0) throw invalid("模型价格参数无效");
    }

    private static void validateBudget(BudgetCommand value) {
        if (value == null
                || blank(value.policyKey())
                || blank(value.scene())
                || blank(value.scopeType())
                || blank(value.policyVersion())
                || !SetValues.SCOPES.contains(value.scopeType())
                || !SetValues.WINDOWS.contains(value.windowType())
                || value.maxTotalTokens() <= 0
                || value.maxCostCny() == null
                || value.maxCostCny().signum() < 0
                || value.maxModelCalls() <= 0
                || value.maxStepRetries() < 0) throw invalid("模型预算参数无效");
    }

    private static void requireStatus(String value) {
        if (!validStatus(value)) throw invalid("状态必须为 active 或 disabled");
    }

    private static boolean validStatus(String value) {
        return "active".equals(value) || "disabled".equals(value);
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) throw invalid(label + " 必须为正数");
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private static String routeValue(RouteCommand value) {
        return value == null
                ? ""
                : String.join(
                        "|",
                        clean(value.providerCode()),
                        clean(value.modelName()),
                        clean(value.routeVersion()),
                        clean(value.priceVersion()),
                        clean(value.budgetPolicyVersion()),
                        clean(value.status()),
                        Integer.toString(value.priority()));
    }

    private static String priceValue(PriceCommand value) {
        return value == null
                ? ""
                : String.join(
                        "|",
                        clean(value.providerCode()),
                        clean(value.modelName()),
                        clean(value.priceVersion()),
                        String.valueOf(value.inputPricePerMillion()),
                        String.valueOf(value.outputPricePerMillion()),
                        clean(value.currency()),
                        String.valueOf(value.effectiveAt()));
    }

    private static String budgetValue(BudgetCommand value) {
        return value == null
                ? ""
                : String.join(
                        "|",
                        clean(value.policyKey()),
                        clean(value.scene()),
                        clean(value.scopeType()),
                        Integer.toString(value.maxTotalTokens()),
                        String.valueOf(value.maxCostCny()),
                        Integer.toString(value.maxModelCalls()),
                        Integer.toString(value.maxStepRetries()),
                        clean(value.windowType()),
                        clean(value.policyVersion()));
    }

    private static String digest(
            String action, String target, String value, AdminWriteCommand command) {
        return sha256(
                action
                        + "|"
                        + target
                        + "|"
                        + value
                        + "|"
                        + (command == null ? 0 : command.revision())
                        + "|"
                        + (command != null && command.confirmed())
                        + "|"
                        + (command == null ? "" : command.confirmationDigest()));
    }

    public static String confirmationDigest(
            String action, String target, String value, long revision) {
        return sha256(action + "|" + target + "|" + value + "|" + revision);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String resultJson(MutationResult result) {
        try {
            return mapper.writeValueAsString(
                    Map.of(
                            "changed", result.changed(),
                            "resourceId", result.resourceId(),
                            "version", result.version(),
                            "revision", result.revision()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("model governance result is not JSON", exception);
        }
    }

    private static final class SetValues {
        private static final java.util.Set<String> SCOPES =
                java.util.Set.of("global", "scene", "user");
        private static final java.util.Set<String> WINDOWS = java.util.Set.of("run", "day");
    }
}
