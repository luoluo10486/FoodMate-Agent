package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.food.port.out.MealPlanRepository;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 餐食计划应用服务；计划内容可以来自模型，但状态和校验由 Java 决定。 */
@Service
@Profile("local")
public class MealPlanServiceImpl implements MealPlanService {
    private static final Set<String> REQUIRED_MEALS = Set.of("breakfast", "lunch", "dinner");
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private final MealPlanRepository store;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public MealPlanServiceImpl(MealPlanRepository store, IdGenerator ids, ObjectMapper mapper) {
        this.store = store;
        this.ids = ids;
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    @Override
    @Transactional
    public PlanView create(long userId, CreateCommand command) {
        validateInput(userId, command);
        String key = optionalIdempotencyKey(command.idempotencyKey());
        String digest = digest("create", command);
        long id = ids.nextId();
        if (key != null) {
            MealPlanRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
            if (previous != null) return replayOrConflict(userId, key, digest);
            reserveAudit(userId, key, digest, "meal_plan.create", id, null);
        }
        JsonNode constraints = constraints(command);
        JsonNode validation = validation(command);
        if (store.insertPlan(
                        new MealPlanRepository.PlanWrite(
                                id,
                                userId,
                                command.sessionId(),
                                command.planName(),
                                command.days(),
                                command.budget(),
                                json(constraints),
                                json(command.daysPlan()),
                                json(validation),
                                "draft",
                                key,
                                1))
                != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "餐食计划写入失败");
        }
        PlanView result =
                view(key == null ? requirePlan(userId, id) : requirePlan(userId, id, false));
        if (key != null) store.completeAudit(userId, key, json(result));
        return result;
    }

    @Override
    public PlanView get(long userId, long mealPlanId) {
        return view(requirePlan(userId, mealPlanId, false));
    }

    @Override
    @Transactional
    public PlanView update(long userId, long mealPlanId, long revision, UpdateCommand command) {
        validateUpdate(command);
        String key = requireIdempotencyKey(command.idempotencyKey());
        String digest = digest("update", mealPlanId, revision, command);
        MealPlanRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) return replayOrConflict(userId, key, digest);
        MealPlanRepository.PlanSnapshot current = requirePlan(userId, mealPlanId, false);
        requireRevision(current, revision);
        reserveAudit(userId, key, digest, "meal_plan.update", mealPlanId, null);
        JsonNode constraints = constraints(command);
        JsonNode validation = validation(command);
        if (store.updatePlan(
                        new MealPlanRepository.UpdatePlanWrite(
                                userId,
                                mealPlanId,
                                revision,
                                command.planName(),
                                command.days(),
                                command.budget(),
                                json(constraints),
                                json(command.daysPlan()),
                                json(validation)))
                != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划版本已变化");
        }
        store.softDeleteShoppingList(userId, mealPlanId);
        PlanView result = view(requirePlan(userId, mealPlanId, false));
        store.completeAudit(userId, key, json(result));
        return result;
    }

    @Override
    @Transactional
    public PlanView validate(long userId, long mealPlanId) {
        return validateInternal(userId, mealPlanId, null, null, true);
    }

    @Override
    @Transactional
    public PlanView validate(long userId, long mealPlanId, String idempotencyKey) {
        return validateInternal(
                userId, mealPlanId, null, requireIdempotencyKey(idempotencyKey), false);
    }

    @Override
    @Transactional
    public PlanView validate(long userId, long mealPlanId, long revision, String idempotencyKey) {
        return validateInternal(
                userId, mealPlanId, revision, requireIdempotencyKey(idempotencyKey), false);
    }

    private PlanView validateInternal(
            long userId,
            long mealPlanId,
            Long expectedRevision,
            String idempotencyKey,
            boolean legacyRepositoryPath) {
        MealPlanRepository.PlanSnapshot plan =
                legacyRepositoryPath
                        ? requirePlan(userId, mealPlanId)
                        : requirePlan(userId, mealPlanId, false);
        long revision = expectedRevision == null ? plan.revision() : expectedRevision;
        if (expectedRevision != null) requireRevision(plan, expectedRevision);
        String digest = digest("validate", mealPlanId, revision);
        if (idempotencyKey != null) {
            MealPlanRepository.IdempotencyRecord previous =
                    store.findIdempotency(userId, idempotencyKey);
            if (previous != null) return replayOrConflict(userId, idempotencyKey, digest);
            reserveAudit(userId, idempotencyKey, digest, "meal_plan.validate", mealPlanId, null);
        }
        if (!"draft".equals(plan.status()) && !"validated".equals(plan.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "只有 draft 或 validated 计划可以校验");
        CreateCommand command = command(plan);
        JsonNode validation = validation(command);
        String status = validation.get("valid").asBoolean() ? "validated" : "draft";
        int updated =
                legacyRepositoryPath
                        ? store.updatePlanStatus(userId, mealPlanId, status, json(validation))
                        : store.updatePlanStatus(
                                userId, mealPlanId, revision, status, json(validation));
        if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "餐食计划状态已变化");
        PlanView result =
                view(
                        legacyRepositoryPath
                                ? requirePlan(userId, mealPlanId)
                                : requirePlan(userId, mealPlanId, false));
        if (idempotencyKey != null) store.completeAudit(userId, idempotencyKey, json(result));
        return result;
    }

    @Override
    @Transactional
    public PlanView save(long userId, long mealPlanId) {
        return saveInternal(userId, mealPlanId, null, null, true);
    }

    @Override
    @Transactional
    public PlanView save(long userId, long mealPlanId, String idempotencyKey) {
        return saveInternal(userId, mealPlanId, null, requireIdempotencyKey(idempotencyKey), false);
    }

    @Override
    @Transactional
    public PlanView save(long userId, long mealPlanId, long revision, String idempotencyKey) {
        return saveInternal(
                userId, mealPlanId, revision, requireIdempotencyKey(idempotencyKey), false);
    }

    private PlanView saveInternal(
            long userId,
            long mealPlanId,
            Long expectedRevision,
            String idempotencyKey,
            boolean legacyRepositoryPath) {
        MealPlanRepository.PlanSnapshot plan =
                legacyRepositoryPath
                        ? requirePlan(userId, mealPlanId)
                        : requirePlan(userId, mealPlanId, false);
        long revision = expectedRevision == null ? plan.revision() : expectedRevision;
        if (expectedRevision != null) requireRevision(plan, expectedRevision);
        String digest = digest("save", mealPlanId, revision);
        if (idempotencyKey != null) {
            MealPlanRepository.IdempotencyRecord previous =
                    store.findIdempotency(userId, idempotencyKey);
            if (previous != null) return replayOrConflict(userId, idempotencyKey, digest);
            reserveAudit(userId, idempotencyKey, digest, "meal_plan.save", mealPlanId, null);
        }
        if (!"validated".equals(plan.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "只有 validated 计划可以保存");
        JsonNode validation = read(plan.validationJson());
        if (!validation.path("valid").asBoolean(false))
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划校验未通过");
        int updated =
                legacyRepositoryPath
                        ? store.updatePlanStatus(userId, mealPlanId, "saved", plan.validationJson())
                        : store.updatePlanStatus(
                                userId, mealPlanId, revision, "saved", plan.validationJson());
        if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "餐食计划状态已变化");
        PlanView result =
                view(
                        legacyRepositoryPath
                                ? requirePlan(userId, mealPlanId)
                                : requirePlan(userId, mealPlanId, false));
        if (idempotencyKey != null) store.completeAudit(userId, idempotencyKey, json(result));
        return result;
    }

    @Override
    @Transactional
    public ShoppingListView shoppingList(long userId, long mealPlanId) {
        MealPlanRepository.PlanSnapshot plan = requirePlan(userId, mealPlanId);
        if (!"saved".equals(plan.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "只有 saved 计划可以生成购物清单");
        MealPlanRepository.ShoppingListSnapshot existing =
                store.findOwnedShoppingList(userId, mealPlanId);
        if (existing != null) return shoppingView(existing);
        ArrayNode items = aggregateShoppingItems(read(plan.planJson()));
        long id = ids.nextId();
        store.insertShoppingList(
                new MealPlanRepository.ShoppingListWrite(
                        id, mealPlanId, userId, json(items), "generated"));
        return shoppingView(store.findOwnedShoppingList(userId, mealPlanId));
    }

    @Override
    @Transactional
    public void delete(long userId, long mealPlanId, long revision, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String digest = digest("delete", mealPlanId, revision);
        MealPlanRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) {
            replayVoidOrConflict(previous, digest);
            return;
        }
        MealPlanRepository.PlanSnapshot current = requirePlan(userId, mealPlanId, false);
        requireRevision(current, revision);
        reserveAudit(userId, key, digest, "meal_plan.delete", mealPlanId, null);
        if (store.softDelete(userId, mealPlanId, revision) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划版本已变化");
        store.softDeleteShoppingList(userId, mealPlanId);
        store.completeAudit(userId, key, "{}");
    }

    @Override
    @Transactional
    public PlanView restore(long userId, long mealPlanId, long revision, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String digest = digest("restore", mealPlanId, revision);
        MealPlanRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous != null) return replayOrConflict(userId, key, digest);
        MealPlanRepository.PlanSnapshot current = requirePlan(userId, mealPlanId, true);
        if (!current.deleted()) throw new BusinessException(ErrorCode.NOT_FOUND, "餐食计划不存在");
        requireRevision(current, revision);
        reserveAudit(userId, key, digest, "meal_plan.restore", mealPlanId, null);
        if (store.restore(userId, mealPlanId, revision) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划版本已变化");
        PlanView result = view(requirePlan(userId, mealPlanId, false));
        store.completeAudit(userId, key, json(result));
        return result;
    }

    private void validateInput(long userId, CreateCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划参数不能为空");
        validateValues(
                userId,
                command.sessionId(),
                command.planName(),
                command.people(),
                command.days(),
                command.budget(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes());
        optionalIdempotencyKey(command.idempotencyKey());
    }

    private void validateUpdate(UpdateCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划参数不能为空");
        validateValues(
                0L,
                null,
                command.planName(),
                command.people(),
                command.days(),
                command.budget(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes());
        requireIdempotencyKey(command.idempotencyKey());
    }

    private void validateValues(
            long userId,
            Long sessionId,
            String planName,
            int people,
            int days,
            BigDecimal budget,
            Integer calorieTarget,
            Integer proteinTarget,
            List<String> allergens,
            List<String> dislikes) {
        if (sessionId != null && userId > 0 && !store.sessionOwned(userId, sessionId))
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源会话不存在");
        if (people < 1 || people > 20)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "人数必须在 1 到 20 之间");
        if (days < 1 || days > 7)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划天数必须在 1 到 7 天之间");
        if (planName != null && planName.length() > 128)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划名称过长");
        if (budget != null && (budget.signum() < 0 || budget.scale() > 2))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "预算无效");
        if (calorieTarget != null && calorieTarget < 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "热量目标无效");
        if (proteinTarget != null && proteinTarget < 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "蛋白质目标无效");
        validateStrings(allergens, "过敏原");
        validateStrings(dislikes, "忌口");
    }

    private void validateStrings(List<String> values, String label) {
        if (values == null) return;
        for (String value : values)
            if (value == null || value.isBlank() || value.length() > 128)
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, label + "无效");
    }

    private JsonNode constraints(CreateCommand command) {
        return constraints(
                command.people(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes());
    }

    private JsonNode constraints(UpdateCommand command) {
        return constraints(
                command.people(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes());
    }

    private JsonNode constraints(
            int people,
            Integer calorieTarget,
            Integer proteinTarget,
            List<String> allergens,
            List<String> dislikes) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("people", people);
        if (calorieTarget != null) node.put("calorie_target", calorieTarget);
        if (proteinTarget != null) node.put("protein_target", proteinTarget);
        node.set("allergens", mapper.valueToTree(allergens));
        node.set("dislikes", mapper.valueToTree(dislikes));
        return node;
    }

    private JsonNode validation(CreateCommand command) {
        return validation(
                command.days(), command.daysPlan(), command.allergens(), command.dislikes());
    }

    private JsonNode validation(UpdateCommand command) {
        return validation(
                command.days(), command.daysPlan(), command.allergens(), command.dislikes());
    }

    private JsonNode validation(
            int days, JsonNode daysPlan, List<String> allergens, List<String> dislikes) {
        ArrayNode errors = JsonNodeFactory.instance.arrayNode();
        ArrayNode warnings = JsonNodeFactory.instance.arrayNode();
        if (!daysPlan.isArray() || daysPlan.size() != days)
            errors.add("days_plan 必须包含与 days 相同数量的天");
        else {
            for (int index = 0; index < daysPlan.size(); index++) {
                JsonNode day = daysPlan.get(index);
                if (!day.isObject()) {
                    errors.add("第 " + (index + 1) + " 天必须是对象");
                    continue;
                }
                Set<String> meals = new HashSet<>();
                for (String meal : REQUIRED_MEALS) {
                    JsonNode mealNode = day.get(meal);
                    if (mealNode == null || !mealNode.isObject())
                        errors.add("第 " + (index + 1) + " 天缺少 " + meal);
                    else meals.add(meal);
                }
                if (meals.size() < REQUIRED_MEALS.size()) continue;
                String serialized = day.toString().toLowerCase(Locale.ROOT);
                for (String forbidden : allergens)
                    if (serialized.contains(forbidden.toLowerCase(Locale.ROOT)))
                        errors.add("第 " + (index + 1) + " 天包含过敏原: " + forbidden);
                for (String disliked : dislikes)
                    if (serialized.contains(disliked.toLowerCase(Locale.ROOT)))
                        warnings.add("第 " + (index + 1) + " 天包含忌口: " + disliked);
            }
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("valid", errors.isEmpty());
        result.set("errors", errors);
        result.set("issues", errors.deepCopy());
        result.set("warnings", warnings);
        result.set("nutrition_summary", JsonNodeFactory.instance.objectNode());
        result.set("budget_summary", JsonNodeFactory.instance.objectNode());
        result.put("checked_at", Instant.now().toString());
        return result;
    }

    private ArrayNode aggregateShoppingItems(JsonNode plan) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        if (!plan.isArray()) return result;
        java.util.Map<String, ObjectNode> merged = new java.util.TreeMap<>();
        for (JsonNode day : plan)
            for (String meal : REQUIRED_MEALS) {
                JsonNode mealNode = day.get(meal);
                JsonNode ingredients = mealNode == null ? null : mealNode.get("ingredients");
                if (ingredients == null || !ingredients.isArray()) continue;
                for (JsonNode ingredient : ingredients) {
                    String name = ingredient.path("name").asText("").trim();
                    String unit = ingredient.path("unit").asText("g").trim();
                    if (name.isBlank()) continue;
                    String key =
                            name.toLowerCase(Locale.ROOT) + "|" + unit.toLowerCase(Locale.ROOT);
                    ObjectNode item =
                            merged.computeIfAbsent(
                                    key, ignored -> JsonNodeFactory.instance.objectNode());
                    item.put("name", name);
                    item.put("unit", unit);
                    BigDecimal amount = decimal(ingredient.get("amount"));
                    BigDecimal total =
                            decimal(item.get("amount"))
                                    .add(amount)
                                    .setScale(3, RoundingMode.HALF_UP);
                    item.put("amount", total);
                }
            }
        merged.values().forEach(result::add);
        return result;
    }

    private CreateCommand command(MealPlanRepository.PlanSnapshot plan) {
        JsonNode constraints = read(plan.constraintsJson());
        return new CreateCommand(
                plan.sessionId(),
                plan.planName(),
                constraints.path("people").asInt(1),
                plan.days(),
                plan.budget(),
                constraints.has("calorie_target")
                        ? constraints.get("calorie_target").asInt()
                        : null,
                constraints.has("protein_target")
                        ? constraints.get("protein_target").asInt()
                        : null,
                strings(constraints.get("allergens")),
                strings(constraints.get("dislikes")),
                read(plan.planJson()));
    }

    private PlanView view(MealPlanRepository.PlanSnapshot plan) {
        JsonNode constraints = read(plan.constraintsJson());
        return new PlanView(
                plan.mealPlanId(),
                plan.sessionId(),
                plan.planName(),
                constraints.path("people").asInt(1),
                plan.days(),
                plan.budget(),
                constraints,
                read(plan.planJson()),
                read(plan.validationJson()),
                plan.status(),
                plan.revision(),
                plan.deleted(),
                plan.createdAt(),
                plan.updatedAt());
    }

    private ShoppingListView shoppingView(MealPlanRepository.ShoppingListSnapshot value) {
        if (value == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "购物清单写入后无法读取");
        return new ShoppingListView(
                value.shoppingListId(),
                value.mealPlanId(),
                read(value.itemsJson()),
                value.status(),
                value.createdAt(),
                value.updatedAt());
    }

    private MealPlanRepository.PlanSnapshot requirePlan(long userId, long mealPlanId) {
        MealPlanRepository.PlanSnapshot value = store.findOwnedPlan(userId, mealPlanId);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "餐食计划不存在");
        return value;
    }

    private MealPlanRepository.PlanSnapshot requirePlan(
            long userId, long mealPlanId, boolean includeDeleted) {
        MealPlanRepository.PlanSnapshot value =
                store.findOwnedPlan(userId, mealPlanId, includeDeleted);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "餐食计划不存在");
        return value;
    }

    private String optionalIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        return requireIdempotencyKey(value);
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 无效");
        return value;
    }

    private void requireRevision(MealPlanRepository.PlanSnapshot plan, long revision) {
        if (plan.revision() != revision)
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划版本已变化");
    }

    private void reserveAudit(
            long userId,
            String key,
            String digest,
            String action,
            long mealPlanId,
            PlanView response) {
        TraceContext trace = TraceContextHolder.currentOrNew();
        if (store.reserveAudit(
                        new MealPlanRepository.AuditWrite(
                                ids.nextId(),
                                userId,
                                trace.requestId(),
                                trace.traceId(),
                                "meal_plan",
                                Long.toString(mealPlanId),
                                action,
                                digest,
                                key,
                                response == null ? "{}" : json(response)))
                != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求已被其他请求占用");
        }
    }

    private static void requireSameDigest(
            MealPlanRepository.IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的请求参数已变化");
    }

    private PlanView replayOrConflict(long userId, String key, String digest) {
        MealPlanRepository.IdempotencyRecord previous = store.findIdempotency(userId, key);
        if (previous == null) throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        requireSameDigest(previous, digest);
        if (!"success".equals(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        return parseResponse(previous.responseJson());
    }

    private void replayVoidOrConflict(
            MealPlanRepository.IdempotencyRecord previous, String digest) {
        requireSameDigest(previous, digest);
        if (!"success".equals(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            mapper.writeValueAsString(
                                                            java.util.Arrays.asList(values))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "计划参数摘要计算失败");
        }
    }

    private String digest(String action, CreateCommand command) {
        return digest(
                action,
                command.sessionId(),
                command.planName(),
                command.people(),
                command.days(),
                command.budget(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes(),
                command.daysPlan());
    }

    private String digest(String action, long mealPlanId, long revision, UpdateCommand command) {
        return digest(
                action,
                mealPlanId,
                revision,
                command.planName(),
                command.people(),
                command.days(),
                command.budget(),
                command.calorieTarget(),
                command.proteinTarget(),
                command.allergens(),
                command.dislikes(),
                command.daysPlan());
    }

    private String digest(String action, long mealPlanId, long revision) {
        return digest(new Object[] {action, mealPlanId, revision});
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "餐食计划 JSON 序列化失败");
        }
    }

    private PlanView parseResponse(String responseJson) {
        try {
            return mapper.readValue(responseJson, PlanView.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "已保存的计划结果无效");
        }
    }

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "餐食计划 JSON 无效");
        }
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(
                value -> {
                    if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
                });
        return List.copyOf(values);
    }

    private static BigDecimal decimal(JsonNode value) {
        return value != null && value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}
