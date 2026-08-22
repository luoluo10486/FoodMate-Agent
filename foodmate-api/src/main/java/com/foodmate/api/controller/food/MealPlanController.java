package com.foodmate.api.controller.food;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.food.MealPlanCreateRequest;
import com.foodmate.api.request.food.MealPlanUpdateRequest;
import com.foodmate.api.response.food.MealPlanResponse;
import com.foodmate.api.response.food.ShoppingListResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 餐食计划和购物清单接口。 */
@RestController
@Profile("local")
@RequestMapping("/api/meal-plans")
public class MealPlanController extends AuthenticatedControllerSupport {
    private final MealPlanService plans;

    public MealPlanController(UserAccountService accounts, MealPlanService plans) {
        super(accounts);
        this.plans = plans;
    }

    @PostMapping
    public ApiResponse<MealPlanResponse> create(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MealPlanCreateRequest body) {
        return ok(
                map(
                        plans.create(
                                user(request).userId(),
                                new MealPlanService.CreateCommand(
                                        body.sessionId(),
                                        body.planName(),
                                        body.people(),
                                        body.days(),
                                        body.budget(),
                                        body.calorieTarget(),
                                        body.proteinTarget(),
                                        body.allergens(),
                                        body.dislikes(),
                                        body.daysPlan(),
                                        idempotencyKey))));
    }

    @GetMapping("/{mealPlanId}")
    public ApiResponse<MealPlanResponse> get(
            HttpServletRequest request, @PathVariable long mealPlanId) {
        return ok(map(plans.get(user(request).userId(), mealPlanId)));
    }

    @GetMapping
    public ApiResponse<List<MealPlanResponse>> list(HttpServletRequest request) {
        return ok(plans.list(user(request).userId()).stream().map(this::map).toList());
    }

    @PatchMapping("/{mealPlanId}")
    public ApiResponse<MealPlanResponse> update(
            HttpServletRequest request,
            @PathVariable long mealPlanId,
            @RequestParam long revision,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MealPlanUpdateRequest body) {
        return ok(
                map(
                        plans.update(
                                user(request).userId(),
                                mealPlanId,
                                revision,
                                new MealPlanService.UpdateCommand(
                                        body.planName(),
                                        body.people(),
                                        body.days(),
                                        body.budget(),
                                        body.calorieTarget(),
                                        body.proteinTarget(),
                                        body.allergens(),
                                        body.dislikes(),
                                        body.daysPlan(),
                                        idempotencyKey))));
    }

    @PostMapping("/{mealPlanId}/validate")
    public ApiResponse<MealPlanResponse> validate(
            HttpServletRequest request,
            @PathVariable long mealPlanId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ok(
                map(plans.validate(user(request).userId(), mealPlanId, revision, idempotencyKey)));
    }

    @PostMapping("/{mealPlanId}/save")
    public ApiResponse<MealPlanResponse> save(
            HttpServletRequest request,
            @PathVariable long mealPlanId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ok(map(plans.save(user(request).userId(), mealPlanId, revision, idempotencyKey)));
    }

    @DeleteMapping("/{mealPlanId}")
    public ApiResponse<Void> delete(
            HttpServletRequest request,
            @PathVariable long mealPlanId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        plans.delete(user(request).userId(), mealPlanId, revision, idempotencyKey);
        return ok(null);
    }

    @PostMapping("/{mealPlanId}/restore")
    public ApiResponse<MealPlanResponse> restore(
            HttpServletRequest request,
            @PathVariable long mealPlanId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ok(map(plans.restore(user(request).userId(), mealPlanId, revision, idempotencyKey)));
    }

    @PostMapping("/{mealPlanId}/shopping-list")
    public ApiResponse<ShoppingListResponse> createShoppingList(
            HttpServletRequest request, @PathVariable long mealPlanId) {
        return ok(map(plans.shoppingList(user(request).userId(), mealPlanId)));
    }

    @GetMapping("/{mealPlanId}/shopping-list")
    public ApiResponse<ShoppingListResponse> shoppingList(
            HttpServletRequest request, @PathVariable long mealPlanId) {
        return ok(map(plans.shoppingList(user(request).userId(), mealPlanId)));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }

    private MealPlanResponse map(MealPlanService.PlanView value) {
        return new MealPlanResponse(
                Long.toString(value.mealPlanId()),
                value.sessionId(),
                value.planName(),
                value.people(),
                value.days(),
                value.budget(),
                value.constraints(),
                value.daysPlan(),
                value.validation(),
                value.status(),
                value.revision(),
                value.deleted(),
                value.createdAt(),
                value.updatedAt());
    }

    private ShoppingListResponse map(MealPlanService.ShoppingListView value) {
        return new ShoppingListResponse(
                Long.toString(value.shoppingListId()),
                Long.toString(value.mealPlanId()),
                value.items(),
                value.status(),
                value.createdAt(),
                value.updatedAt());
    }
}
