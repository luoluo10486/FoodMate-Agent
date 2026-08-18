package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.food.port.out.FoodLogRepository;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.food.enums.MealType;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 权威饮食记录写入用例；手工页面和后续 Agent 工具必须复用此服务。 */
@Service
@Profile("local")
public class FoodLogServiceImpl implements FoodLogService {
    private static final int MAX_ITEMS = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final FoodLogRepository store;
    private final IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public FoodLogServiceImpl(FoodLogRepository store, IdGenerator ids) {
        this.store = store;
        this.ids = ids;
    }

    @Transactional
    @Override
    public FoodLogView create(long userId, CreateCommand command) {
        validateCreate(userId, command);
        String key = requireIdempotencyKey(command.idempotencyKey());
        String digest = digest(command);
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) {
            return replayOrConflict(userId, key, digest);
        }

        long foodLogId = ids.nextId();
        if (store.reserveAudit(audit(userId, key, digest, "food_log.create", foodLogId, null)) != 1)
            return replayOrConflict(userId, key, digest);
        if (store.insertFoodLog(
                        new FoodLogRepository.FoodLogWrite(
                                foodLogId,
                                userId,
                                command.sessionId(),
                                command.agentRunId(),
                                command.mealTime(),
                                command.mealType().code(),
                                command.notes(),
                                command.source(),
                                key,
                                1))
                != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "饮食记录关联资源不存在");
        }
        for (int i = 0; i < command.items().size(); i++) {
            ItemCommand item = command.items().get(i);
            FoodLogRepository.FoodLogItemWrite nutrition = nutrition(item, foodLogId, i, userId);
            store.insertItem(
                    new FoodLogRepository.FoodLogItemWrite(
                            ids.nextId(),
                            nutrition.foodLogId(),
                            nutrition.itemOrder(),
                            nutrition.rawName(),
                            nutrition.amount(),
                            nutrition.unit(),
                            nutrition.userId(),
                            nutrition.nutritionFoodId(),
                            nutrition.normalizedAmount(),
                            nutrition.normalizedUnit(),
                            nutrition.conversionId(),
                            nutrition.caloriesKcal(),
                            nutrition.proteinG(),
                            nutrition.fatG(),
                            nutrition.carbsG(),
                            nutrition.nutritionStatus(),
                            nutrition.nutritionSource(),
                            nutrition.nutritionVersion()));
        }
        FoodLogView result = view(requireSnapshot(userId, foodLogId, false));
        store.completeAudit(userId, key, auditSummary(result));
        return result;
    }

    @Transactional
    @Override
    public FoodLogView update(long userId, long foodLogId, long revision, UpdateCommand command) {
        validateUpdate(command);
        String key = requireIdempotencyKey(command.idempotencyKey());
        String digest = digest(command, foodLogId, revision);
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) return replayOrConflict(userId, key, digest);

        FoodLogRepository.FoodLogSnapshot current = requireSnapshot(userId, foodLogId, false);
        requireRevision(current, revision);
        if (store.reserveAudit(audit(userId, key, digest, "food_log.update", foodLogId, null)) != 1)
            return replayOrConflict(userId, key, digest);
        if (store.updateFoodLog(
                        new FoodLogRepository.UpdateFoodLogWrite(
                                userId,
                                foodLogId,
                                revision,
                                command.mealTime(),
                                command.mealType().code(),
                                command.notes()))
                != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "饮食记录已被修改");
        }
        store.softDeleteItems(userId, foodLogId);
        for (int i = 0; i < command.items().size(); i++) {
            ItemCommand item = command.items().get(i);
            FoodLogRepository.FoodLogItemWrite nutrition = nutrition(item, foodLogId, i, userId);
            store.insertItem(
                    new FoodLogRepository.FoodLogItemWrite(
                            ids.nextId(),
                            nutrition.foodLogId(),
                            nutrition.itemOrder(),
                            nutrition.rawName(),
                            nutrition.amount(),
                            nutrition.unit(),
                            nutrition.userId(),
                            nutrition.nutritionFoodId(),
                            nutrition.normalizedAmount(),
                            nutrition.normalizedUnit(),
                            nutrition.conversionId(),
                            nutrition.caloriesKcal(),
                            nutrition.proteinG(),
                            nutrition.fatG(),
                            nutrition.carbsG(),
                            nutrition.nutritionStatus(),
                            nutrition.nutritionSource(),
                            nutrition.nutritionVersion()));
        }
        FoodLogView result = view(requireSnapshot(userId, foodLogId, false));
        store.completeAudit(userId, key, auditSummary(result));
        return result;
    }

    @Override
    public List<FoodLogView> list(long userId, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "时间范围无效");
        if (to.minusSeconds(60L * 60 * 24 * 31).isAfter(from))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "时间范围不能超过 31 天");
        return store.findVisible(userId, from, to).stream().map(FoodLogServiceImpl::view).toList();
    }

    @Transactional
    @Override
    public void delete(long userId, long foodLogId, long revision, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String digest = digest("delete", foodLogId, revision);
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) {
            replayVoidOrConflict(previous, digest);
            return;
        }
        FoodLogRepository.FoodLogSnapshot current = requireSnapshot(userId, foodLogId, false);
        requireRevision(current, revision);
        if (store.reserveAudit(audit(userId, key, digest, "food_log.delete", foodLogId, null)) != 1)
            throw concurrentIdempotencyConflict(userId, key, digest);
        if (store.softDelete(userId, foodLogId, revision) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "饮食记录已被修改");
        store.completeAudit(userId, key, "{}");
    }

    @Transactional
    @Override
    public FoodLogView restore(long userId, long foodLogId, long revision, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String digest = digest("restore", foodLogId, revision);
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) return replayOrConflict(userId, key, digest);
        FoodLogRepository.FoodLogSnapshot current = store.findOwned(userId, foodLogId, true);
        if (current == null || !current.deleted())
            throw new BusinessException(ErrorCode.NOT_FOUND, "饮食记录不存在");
        requireRevision(current, revision);
        if (store.reserveAudit(audit(userId, key, digest, "food_log.restore", foodLogId, null))
                != 1) return replayOrConflict(userId, key, digest);
        if (store.restore(userId, foodLogId, revision) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "饮食记录已被修改");
        FoodLogView result = view(requireSnapshot(userId, foodLogId, false));
        store.completeAudit(userId, key, auditSummary(result));
        return result;
    }

    private void validateCreate(long userId, CreateCommand command) {
        if (command == null || command.mealTime() == null || command.mealType() == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用餐时间和餐别不能为空");
        if (command.items().isEmpty() || command.items().size() > MAX_ITEMS)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "食材明细数量无效");
        if (command.notes() != null && command.notes().length() > 4000)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "备注过长");
        if (command.sessionId() != null && !store.sessionOwned(userId, command.sessionId()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源会话不存在");
        if (command.agentRunId() != null && !store.agentRunOwned(userId, command.agentRunId()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源 AgentRun 不存在");
        for (ItemCommand item : command.items()) {
            if (item == null
                    || item.rawName() == null
                    || item.rawName().isBlank()
                    || item.rawName().length() > 255
                    || item.amount() == null
                    || item.amount().signum() <= 0
                    || item.amount().scale() > 3
                    || item.unit() == null
                    || item.unit().isBlank()
                    || item.unit().length() > 32) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "食材明细无效");
            }
        }
    }

    private void validateUpdate(UpdateCommand command) {
        if (command == null || command.mealTime() == null || command.mealType() == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用餐时间和餐别不能为空");
        if (command.items().isEmpty() || command.items().size() > MAX_ITEMS)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "食材明细数量无效");
        if (command.notes() != null && command.notes().length() > 4000)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "备注过长");
        for (ItemCommand item : command.items()) {
            if (item == null
                    || item.rawName() == null
                    || item.rawName().isBlank()
                    || item.rawName().length() > 255
                    || item.amount() == null
                    || item.amount().signum() <= 0
                    || item.amount().scale() > 3
                    || item.unit() == null
                    || item.unit().isBlank()
                    || item.unit().length() > 32) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "食材明细无效");
            }
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 无效");
        return value;
    }

    private FoodLogRepository.FoodLogItemWrite nutrition(
            ItemCommand item, long foodLogId, int itemOrder, long userId) {
        String rawName = item.rawName().trim();
        String sourceUnit = normalizeUnit(item.unit());
        FoodLogRepository.NutritionFoodLookup food =
                store.findNutritionFood(normalizeName(rawName));
        if (food == null) {
            return pendingItem(item, foodLogId, itemOrder, userId);
        }

        BigDecimal normalizedAmount = null;
        Long conversionId = null;
        String normalizedUnit = null;
        String nutritionSource = food.sourceName();
        String nutritionVersion = food.sourceVersion();
        if (food.basisUnit().equals(sourceUnit)) {
            normalizedAmount = item.amount().setScale(3, RoundingMode.HALF_UP);
            normalizedUnit = food.basisUnit();
        } else {
            FoodLogRepository.UnitConversionLookup conversion =
                    store.findUnitConversion(food.nutritionFoodId(), sourceUnit, food.basisUnit());
            if (conversion == null) {
                return pendingItem(item, foodLogId, itemOrder, userId);
            }
            normalizedAmount =
                    item.amount()
                            .multiply(conversion.multiplier())
                            .setScale(3, RoundingMode.HALF_UP);
            normalizedUnit = conversion.targetUnit();
            conversionId = conversion.conversionId();
            nutritionSource = food.sourceName() + ";" + conversion.sourceName();
            // The conversion version is more specific and includes the same food FDC ID plus
            // the reviewed portion sequence; keep it as the bounded 64-char snapshot version.
            nutritionVersion = conversion.sourceVersion();
        }
        BigDecimal factor = normalizedAmount.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        return new FoodLogRepository.FoodLogItemWrite(
                0L,
                foodLogId,
                itemOrder,
                rawName,
                item.amount(),
                item.unit().trim(),
                userId,
                food.nutritionFoodId(),
                normalizedAmount,
                normalizedUnit,
                conversionId,
                nutrient(factor, food.caloriesKcalPer100()),
                nutrient(factor, food.proteinGPer100()),
                nutrient(factor, food.fatGPer100()),
                nutrient(factor, food.carbsGPer100()),
                "matched",
                nutritionSource,
                nutritionVersion);
    }

    private FoodLogRepository.FoodLogItemWrite pendingItem(
            ItemCommand item, long foodLogId, int itemOrder, long userId) {
        return new FoodLogRepository.FoodLogItemWrite(
                0L,
                foodLogId,
                itemOrder,
                item.rawName().trim(),
                item.amount(),
                item.unit().trim(),
                userId);
    }

    private static BigDecimal nutrient(BigDecimal factor, BigDecimal per100) {
        return factor.multiply(per100).setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeUnit(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "克", "g" -> "g";
            case "毫升", "ml" -> "ml";
            case "杯" -> "cup";
            case "大号", "大个" -> "large";
            case "中号", "中等" -> "medium";
            case "盎司" -> "oz";
            case "汤匙", "大匙" -> "tbsp";
            default -> value.trim().toLowerCase(Locale.ROOT);
        };
    }

    private FoodLogRepository.AuditWrite audit(
            long userId,
            String key,
            String digest,
            String action,
            long foodLogId,
            FoodLogView response) {
        TraceContext trace = TraceContextHolder.currentOrNew();
        return new FoodLogRepository.AuditWrite(
                ids.nextId(),
                userId,
                trace.requestId(),
                trace.traceId(),
                "food_log",
                Long.toString(foodLogId),
                action,
                digest,
                key,
                response == null ? "{}" : auditSummary(response));
    }

    private FoodLogRepository.FoodLogSnapshot requireSnapshot(
            long userId, long foodLogId, boolean includeDeleted) {
        FoodLogRepository.FoodLogSnapshot result =
                store.findOwned(userId, foodLogId, includeDeleted);
        if (result == null) throw new BusinessException(ErrorCode.NOT_FOUND, "饮食记录不存在");
        return result;
    }

    private static void requireRevision(FoodLogRepository.FoodLogSnapshot current, long revision) {
        if (current.revision() != revision)
            throw new BusinessException(ErrorCode.CONFLICT, "饮食记录版本已变化");
    }

    private static void requireSameDigest(
            FoodLogRepository.IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的请求参数已变化");
    }

    private String digest(CreateCommand command) {
        return digest(
                "create",
                command.sessionId(),
                command.agentRunId(),
                command.mealTime(),
                command.mealType().code(),
                command.notes(),
                command.items());
    }

    private String digest(UpdateCommand command, long foodLogId, long revision) {
        return digest(
                "update",
                foodLogId,
                revision,
                command.mealTime(),
                command.mealType().code(),
                command.notes(),
                command.items());
    }

    private String digest(Object... values) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(java.util.Arrays.asList(values));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot calculate food log request digest", exception);
        }
    }

    private FoodLogView replayOrConflict(long userId, String key, String digest) {
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous == null) throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        requireSameDigest(previous, digest);
        if (!"success".equals(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        return replayResponse(userId, previous.responseJson());
    }

    private void replayVoidOrConflict(FoodLogRepository.IdempotencyRecord previous, String digest) {
        requireSameDigest(previous, digest);
        if (!"success".equals(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
    }

    private BusinessException concurrentIdempotencyConflict(
            long userId, String key, String digest) {
        FoodLogRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous == null) return new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        requireSameDigest(previous, digest);
        return new BusinessException(
                ErrorCode.CONFLICT,
                "success".equals(previous.result()) ? "幂等请求已完成，请重放原请求" : "幂等请求正在处理中");
    }

    private String auditSummary(FoodLogView view) {
        try {
            return mapper.writeValueAsString(
                    java.util.Map.of(
                            "resource_id", view.foodLogId(),
                            "revision", view.revision(),
                            "status", view.deleted() ? "deleted" : "active"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize food log audit summary", exception);
        }
    }

    private FoodLogView replayResponse(long userId, String responseJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode summary = mapper.readTree(responseJson);
            long foodLogId = summary.path("resource_id").asLong(0);
            if (foodLogId > 0) return view(requireSnapshot(userId, foodLogId, false));
            // Compatibility with pre-M1-6 idempotency records. New records never retain
            // notes/items.
            return mapper.treeToValue(summary, FoodLogView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored food log result is invalid", exception);
        }
    }

    private static FoodLogView view(FoodLogRepository.FoodLogSnapshot value) {
        return new FoodLogView(
                value.foodLogId(),
                value.sessionId(),
                value.agentRunId(),
                value.mealTime(),
                MealType.fromCode(value.mealType()),
                value.notes(),
                value.source(),
                value.revision(),
                value.deleted(),
                value.createdAt(),
                value.updatedAt(),
                value.items().stream()
                        .map(
                                item ->
                                        new ItemView(
                                                item.foodLogItemId(),
                                                item.itemOrder(),
                                                item.rawName(),
                                                item.amount(),
                                                item.unit(),
                                                item.nutritionStatus(),
                                                item.caloriesKcal(),
                                                item.proteinG(),
                                                item.fatG(),
                                                item.carbsG()))
                        .toList());
    }
}
