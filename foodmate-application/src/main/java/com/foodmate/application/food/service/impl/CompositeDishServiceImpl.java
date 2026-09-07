package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.food.port.out.CompositeDishRepository;
import com.foodmate.application.food.port.out.FoodLogRepository;
import com.foodmate.application.food.service.CompositeDishService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 复合菜应用服务；所有营养值由 PostgreSQL 目录回源后计算。 */
@Service
@Profile("local")
public class CompositeDishServiceImpl implements CompositeDishService {
    private static final int MAX_COMPONENTS = 50;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CompositeDishRepository dishes;
    private final FoodLogRepository foods;
    private final IdGenerator ids;
    private final OperationAuditService audit;
    private final ObjectMapper mapper;

    public CompositeDishServiceImpl(
            CompositeDishRepository dishes,
            FoodLogRepository foods,
            IdGenerator ids,
            ObjectMapper mapper,
            OperationAuditService audit) {
        this.dishes = dishes;
        this.foods = foods;
        this.ids = ids;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.audit = Objects.requireNonNull(audit, "OperationAuditService is required");
    }

    @Override
    @Transactional
    public CompositeDishView create(long userId, CreateCommand command) {
        validateCreate(command);
        String key = requireKey(command.idempotencyKey());
        String digest = digest("create", command);
        CompositeDishRepository.IdempotencyRecord previous = dishes.findIdempotency(userId, key);
        if (previous != null) return replay(userId, key, digest, previous);
        long id = ids.nextId();
        boolean reserved = false;
        try {
            reserved = reserve(userId, id, "composite_dish.create", digest, key);
            Calculated calculated =
                    calculate(command.totalServings(), command.components(), id, userId);
            if (dishes.insertDish(
                            new CompositeDishRepository.DishWrite(
                                    id,
                                    userId,
                                    command.dishName().trim(),
                                    command.totalServings(),
                                    calculated.caloriesPerServing(),
                                    calculated.proteinPerServing(),
                                    calculated.fatPerServing(),
                                    calculated.carbsPerServing(),
                                    calculated.source(),
                                    key,
                                    1))
                    != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "复合菜写入失败");
            }
            calculated.items().forEach(dishes::insertItem);
            CompositeDishView result = view(requireDish(userId, id, false));
            audit.complete(userId, key, summary(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailure(
                    userId,
                    Long.toString(id),
                    "composite_dish.create",
                    key,
                    digest,
                    reserved,
                    exception);
            throw exception;
        }
    }

    @Override
    public CompositeDishView get(long userId, long compositeDishId) {
        return view(requireDish(userId, compositeDishId, false));
    }

    @Override
    public List<CompositeDishView> list(long userId) {
        return dishes.findOwnedList(userId).stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public CompositeDishView update(
            long userId, long compositeDishId, long revision, UpdateCommand command) {
        validateUpdate(command);
        String key = requireKey(command.idempotencyKey());
        String digest = digest("update", compositeDishId, revision, command);
        CompositeDishRepository.IdempotencyRecord previous = dishes.findIdempotency(userId, key);
        if (previous != null) return replay(userId, key, digest, previous);
        CompositeDishRepository.DishSnapshot current = requireDish(userId, compositeDishId, false);
        requireRevision(current, revision);
        boolean reserved = false;
        try {
            reserved = reserve(userId, compositeDishId, "composite_dish.update", digest, key);
            Calculated calculated =
                    calculate(
                            command.totalServings(), command.components(), compositeDishId, userId);
            if (dishes.updateDish(
                            new CompositeDishRepository.UpdateDishWrite(
                                    userId,
                                    compositeDishId,
                                    revision,
                                    command.dishName().trim(),
                                    command.totalServings(),
                                    calculated.caloriesPerServing(),
                                    calculated.proteinPerServing(),
                                    calculated.fatPerServing(),
                                    calculated.carbsPerServing(),
                                    calculated.source()))
                    != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "复合菜版本已变化");
            }
            dishes.softDeleteItems(userId, compositeDishId);
            calculated.items().forEach(dishes::insertItem);
            CompositeDishView result = view(requireDish(userId, compositeDishId, false));
            audit.complete(userId, key, summary(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailure(
                    userId,
                    Long.toString(compositeDishId),
                    "composite_dish.update",
                    key,
                    digest,
                    reserved,
                    exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void delete(long userId, long compositeDishId, long revision, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        String digest = digest("delete", compositeDishId, revision);
        CompositeDishRepository.IdempotencyRecord previous = dishes.findIdempotency(userId, key);
        if (previous != null) {
            requireDigest(previous, digest);
            return;
        }
        CompositeDishRepository.DishSnapshot current = requireDish(userId, compositeDishId, false);
        requireRevision(current, revision);
        boolean reserved = false;
        try {
            reserved = reserve(userId, compositeDishId, "composite_dish.delete", digest, key);
            if (dishes.softDelete(userId, compositeDishId, revision) != 1)
                throw new BusinessException(ErrorCode.CONFLICT, "复合菜版本已变化");
            dishes.softDeleteItems(userId, compositeDishId);
            audit.complete(userId, key, "{}");
        } catch (RuntimeException exception) {
            recordFailure(
                    userId,
                    Long.toString(compositeDishId),
                    "composite_dish.delete",
                    key,
                    digest,
                    reserved,
                    exception);
            throw exception;
        }
    }

    private Calculated calculate(
            BigDecimal servings, List<ComponentCommand> components, long dishId, long userId) {
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        StringBuilder source = new StringBuilder();
        List<CompositeDishRepository.ItemWrite> items = new java.util.ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            ComponentCommand component = components.get(index);
            FoodLogRepository.NutritionFoodLookup food =
                    foods.findNutritionFoodById(component.nutritionFoodId());
            if (food == null) throw new BusinessException(ErrorCode.NOT_FOUND, "营养目录食材不存在");
            String sourceUnit = normalizeUnit(component.unit());
            BigDecimal normalizedAmount = component.amount();
            Long conversionId = null;
            String normalizedUnit = food.basisUnit();
            if (!food.basisUnit().equals(sourceUnit)) {
                FoodLogRepository.UnitConversionLookup conversion =
                        foods.findUnitConversion(
                                component.nutritionFoodId(), sourceUnit, food.basisUnit());
                if (conversion == null)
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "复合菜食材单位没有可靠换算");
                normalizedAmount = component.amount().multiply(conversion.multiplier());
                conversionId = conversion.conversionId();
                source.append(';')
                        .append(conversion.sourceName())
                        .append(':')
                        .append(conversion.sourceVersion());
            }
            normalizedAmount = normalizedAmount.setScale(3, RoundingMode.HALF_UP);
            BigDecimal factor = normalizedAmount.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
            BigDecimal itemCalories = nutrient(factor, food.caloriesKcalPer100());
            BigDecimal itemProtein = nutrient(factor, food.proteinGPer100());
            BigDecimal itemFat = nutrient(factor, food.fatGPer100());
            BigDecimal itemCarbs = nutrient(factor, food.carbsGPer100());
            calories = calories.add(itemCalories);
            protein = protein.add(itemProtein);
            fat = fat.add(itemFat);
            carbs = carbs.add(itemCarbs);
            source.append(source.length() == 0 ? "" : ";")
                    .append(food.sourceName())
                    .append(':')
                    .append(food.sourceVersion());
            items.add(
                    new CompositeDishRepository.ItemWrite(
                            ids.nextId(),
                            dishId,
                            index,
                            component.nutritionFoodId(),
                            component.rawName().trim(),
                            component.amount(),
                            component.unit().trim(),
                            normalizedAmount,
                            normalizedUnit,
                            conversionId,
                            itemCalories,
                            itemProtein,
                            itemFat,
                            itemCarbs,
                            userId));
        }
        return new Calculated(
                calories.divide(servings, 8, RoundingMode.HALF_UP)
                        .setScale(4, RoundingMode.HALF_UP),
                protein.divide(servings, 8, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP),
                fat.divide(servings, 8, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP),
                carbs.divide(servings, 8, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP),
                source.toString(),
                items);
    }

    private void validateCreate(CreateCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "复合菜不能为空");
        validateCommon(command.dishName(), command.totalServings(), command.components());
    }

    private void validateUpdate(UpdateCommand command) {
        if (command == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "复合菜不能为空");
        validateCommon(command.dishName(), command.totalServings(), command.components());
    }

    private void validateCommon(
            String name, BigDecimal servings, List<ComponentCommand> components) {
        if (name == null
                || name.isBlank()
                || name.length() > 128
                || servings == null
                || servings.signum() <= 0
                || servings.scale() > 3
                || components == null
                || components.isEmpty()
                || components.size() > MAX_COMPONENTS)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "复合菜名称、份数或食材组成无效");
        for (ComponentCommand component : components) {
            if (component == null
                    || component.nutritionFoodId() <= 0
                    || component.rawName() == null
                    || component.rawName().isBlank()
                    || component.rawName().length() > 255
                    || component.amount() == null
                    || component.amount().signum() <= 0
                    || component.amount().scale() > 3
                    || component.unit() == null
                    || component.unit().isBlank()
                    || component.unit().length() > 32)
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "复合菜食材组成无效");
        }
    }

    private boolean reserve(long userId, long targetId, String action, String digest, String key) {
        if (audit.reserve(
                        userId,
                        "composite_dish",
                        Long.toString(targetId),
                        action,
                        digest,
                        key,
                        Map.of("operation", action))
                != 1) throw new BusinessException(ErrorCode.CONFLICT, "幂等键已被其他请求使用");
        return true;
    }

    private void recordFailure(
            long userId,
            String targetId,
            String action,
            String key,
            String digest,
            boolean reserved,
            RuntimeException exception) {
        if (key == null || digest == null) return;
        Runnable record =
                () ->
                        audit.recordFailure(
                                userId,
                                "composite_dish",
                                targetId,
                                action,
                                "failed",
                                errorCode(exception),
                                digest,
                                key,
                                Map.of("failure", action));
        if (reserved && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) record.run();
                        }
                    });
        } else {
            record.run();
        }
    }

    private CompositeDishView replay(
            long userId,
            String key,
            String digest,
            CompositeDishRepository.IdempotencyRecord previous) {
        requireDigest(previous, digest);
        if (!"success".equals(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中");
        try {
            long id = mapper.readTree(previous.responseJson()).path("resource_id").asLong(0);
            return view(requireDish(userId, id, false));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "复合菜幂等结果无效");
        }
    }

    private static void requireDigest(
            CompositeDishRepository.IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应请求已变化");
    }

    private CompositeDishRepository.DishSnapshot requireDish(
            long userId, long id, boolean includeDeleted) {
        CompositeDishRepository.DishSnapshot value = dishes.findOwned(userId, id, includeDeleted);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "复合菜不存在");
        return value;
    }

    private static void requireRevision(
            CompositeDishRepository.DishSnapshot current, long revision) {
        if (current.revision() != revision)
            throw new BusinessException(ErrorCode.CONFLICT, "复合菜版本已变化");
    }

    private CompositeDishView view(CompositeDishRepository.DishSnapshot value) {
        return new CompositeDishView(
                value.compositeDishId(),
                value.dishName(),
                value.totalServings(),
                value.caloriesKcalPerServing(),
                value.proteinGPerServing(),
                value.fatGPerServing(),
                value.carbsGPerServing(),
                value.nutritionSource(),
                value.revision(),
                value.deleted(),
                value.createdAt(),
                value.updatedAt(),
                value.items().stream()
                        .map(
                                item ->
                                        new ComponentView(
                                                item.itemId(),
                                                item.itemOrder(),
                                                item.nutritionFoodId(),
                                                item.rawName(),
                                                item.amount(),
                                                item.unit(),
                                                item.normalizedAmount(),
                                                item.normalizedUnit(),
                                                item.caloriesKcal(),
                                                item.proteinG(),
                                                item.fatG(),
                                                item.carbsG()))
                        .toList());
    }

    private String summary(CompositeDishView value) {
        return "{\"resource_id\":"
                + value.compositeDishId()
                + ",\"revision\":"
                + value.revision()
                + "}";
    }

    private String requireKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 无效");
        return value;
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(List.of(values))));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算复合菜请求摘要", exception);
        }
    }

    private static String errorCode(RuntimeException exception) {
        return exception instanceof BusinessException business
                ? business.errorCode().code()
                : ErrorCode.INTERNAL_ERROR.code();
    }

    private static BigDecimal nutrient(BigDecimal factor, BigDecimal per100) {
        return factor.multiply(per100).setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizeUnit(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "克", "g" -> "g";
            case "公斤", "千克", "kg" -> "kg";
            case "毫克", "mg" -> "mg";
            case "毫升", "ml" -> "ml";
            case "杯" -> "cup";
            case "大号", "大个" -> "large";
            case "中号", "中等" -> "medium";
            case "盎司", "oz" -> "oz";
            case "磅", "lb", "lbs" -> "lb";
            case "汤匙", "大匙" -> "tbsp";
            default -> value.trim().toLowerCase(Locale.ROOT);
        };
    }

    private record Calculated(
            BigDecimal caloriesPerServing,
            BigDecimal proteinPerServing,
            BigDecimal fatPerServing,
            BigDecimal carbsPerServing,
            String source,
            List<CompositeDishRepository.ItemWrite> items) {}
}
