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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
        long id = ids.nextId();
        JsonNode constraints = constraints(command);
        JsonNode validation = validation(command);
        store.insertPlan(
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
                        "draft"));
        return view(requirePlan(userId, id));
    }

    @Override
    @Transactional
    public PlanView validate(long userId, long mealPlanId) {
        MealPlanRepository.PlanSnapshot plan = requirePlan(userId, mealPlanId);
        if (!"draft".equals(plan.status()) && !"validated".equals(plan.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "只有 draft 或 validated 计划可以校验");
        CreateCommand command = command(plan);
        JsonNode validation = validation(command);
        String status = validation.get("valid").asBoolean() ? "validated" : "draft";
        if (store.updatePlanStatus(userId, mealPlanId, status, json(validation)) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划状态已变化");
        return view(requirePlan(userId, mealPlanId));
    }

    @Override
    @Transactional
    public PlanView save(long userId, long mealPlanId) {
        MealPlanRepository.PlanSnapshot plan = requirePlan(userId, mealPlanId);
        if (!"validated".equals(plan.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "只有 validated 计划可以保存");
        JsonNode validation = read(plan.validationJson());
        if (!validation.path("valid").asBoolean(false))
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划校验未通过");
        if (store.updatePlanStatus(userId, mealPlanId, "saved", plan.validationJson()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "餐食计划状态已变化");
        return view(requirePlan(userId, mealPlanId));
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

    private void validateInput(long userId, CreateCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划参数不能为空");
        if (command.sessionId() != null && !store.sessionOwned(userId, command.sessionId()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源会话不存在");
        if (command.people() < 1 || command.people() > 20)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "人数必须在 1 到 20 之间");
        if (command.days() < 1 || command.days() > 7)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划天数必须在 1 到 7 天之间");
        if (command.planName() != null && command.planName().length() > 128)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划名称过长");
        if (command.budget() != null
                && (command.budget().signum() < 0 || command.budget().scale() > 2))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "预算无效");
        if (command.calorieTarget() != null && command.calorieTarget() < 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "热量目标无效");
        if (command.proteinTarget() != null && command.proteinTarget() < 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "蛋白质目标无效");
    }

    private JsonNode constraints(CreateCommand command) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("people", command.people());
        if (command.calorieTarget() != null) node.put("calorie_target", command.calorieTarget());
        if (command.proteinTarget() != null) node.put("protein_target", command.proteinTarget());
        node.set("allergens", mapper.valueToTree(command.allergens()));
        node.set("dislikes", mapper.valueToTree(command.dislikes()));
        return node;
    }

    private JsonNode validation(CreateCommand command) {
        ArrayNode errors = JsonNodeFactory.instance.arrayNode();
        ArrayNode warnings = JsonNodeFactory.instance.arrayNode();
        if (!command.daysPlan().isArray() || command.daysPlan().size() != command.days())
            errors.add("days_plan 必须包含与 days 相同数量的天");
        else {
            for (int index = 0; index < command.daysPlan().size(); index++) {
                JsonNode day = command.daysPlan().get(index);
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
                for (String forbidden : command.allergens())
                    if (serialized.contains(forbidden.toLowerCase(Locale.ROOT)))
                        errors.add("第 " + (index + 1) + " 天包含过敏原: " + forbidden);
                for (String disliked : command.dislikes())
                    if (serialized.contains(disliked.toLowerCase(Locale.ROOT)))
                        warnings.add("第 " + (index + 1) + " 天包含忌口: " + disliked);
            }
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("valid", errors.isEmpty());
        result.set("errors", errors);
        result.set("warnings", warnings);
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

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "餐食计划 JSON 无效");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "餐食计划 JSON 序列化失败");
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
