package com.foodmate.api.controller.food;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.food.CompositeDishCreateRequest;
import com.foodmate.api.request.food.CompositeDishUpdateRequest;
import com.foodmate.api.response.food.CompositeDishResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.CompositeDishService;
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

/** 用户复合菜轻量管理接口；不承担完整菜谱后台职责。 */
@RestController
@Profile("local")
@RequestMapping("/api/composite-dishes")
public class CompositeDishController extends AuthenticatedControllerSupport {
    private final CompositeDishService dishes;

    public CompositeDishController(UserAccountService accounts, CompositeDishService dishes) {
        super(accounts);
        this.dishes = dishes;
    }

    @PostMapping
    public ApiResponse<CompositeDishResponse> create(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompositeDishCreateRequest body) {
        return ok(
                CompositeDishResponse.map(
                        dishes.create(
                                user(request).userId(),
                                new CompositeDishService.CreateCommand(
                                        body.dishName(),
                                        body.totalServings(),
                                        body.components().stream()
                                                .map(
                                                        component ->
                                                                new CompositeDishService
                                                                        .ComponentCommand(
                                                                        component.nutritionFoodId(),
                                                                        component.rawName(),
                                                                        component.amount(),
                                                                        component.unit()))
                                                .toList(),
                                        idempotencyKey))));
    }

    @GetMapping
    public ApiResponse<List<CompositeDishResponse>> list(HttpServletRequest request) {
        return ok(
                dishes.list(user(request).userId()).stream()
                        .map(CompositeDishResponse::map)
                        .toList());
    }

    @GetMapping("/{compositeDishId}")
    public ApiResponse<CompositeDishResponse> get(
            HttpServletRequest request, @PathVariable long compositeDishId) {
        return ok(CompositeDishResponse.map(dishes.get(user(request).userId(), compositeDishId)));
    }

    @PatchMapping("/{compositeDishId}")
    public ApiResponse<CompositeDishResponse> update(
            HttpServletRequest request,
            @PathVariable long compositeDishId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompositeDishUpdateRequest body) {
        return ok(
                CompositeDishResponse.map(
                        dishes.update(
                                user(request).userId(),
                                compositeDishId,
                                revision,
                                new CompositeDishService.UpdateCommand(
                                        body.dishName(),
                                        body.totalServings(),
                                        body.components().stream()
                                                .map(
                                                        component ->
                                                                new CompositeDishService
                                                                        .ComponentCommand(
                                                                        component.nutritionFoodId(),
                                                                        component.rawName(),
                                                                        component.amount(),
                                                                        component.unit()))
                                                .toList(),
                                        idempotencyKey))));
    }

    @DeleteMapping("/{compositeDishId}")
    public ApiResponse<Void> delete(
            HttpServletRequest request,
            @PathVariable long compositeDishId,
            @RequestParam long revision,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        dishes.delete(user(request).userId(), compositeDishId, revision, idempotencyKey);
        return ok(null);
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
