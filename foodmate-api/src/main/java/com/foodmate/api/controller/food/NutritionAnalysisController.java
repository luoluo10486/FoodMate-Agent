package com.foodmate.api.controller.food;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.response.food.NutritionAnalysisResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.NutritionAnalysisService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 营养分析查询接口。 */
@RestController
@Profile("local")
@RequestMapping("/api/nutrition-analysis")
public class NutritionAnalysisController extends AuthenticatedControllerSupport {
    private final NutritionAnalysisService analysis;

    public NutritionAnalysisController(
            UserAccountService accounts, NutritionAnalysisService analysis) {
        super(accounts);
        this.analysis = analysis;
    }

    @GetMapping
    public ApiResponse<NutritionAnalysisResponse> analyze(
            HttpServletRequest request, @RequestParam(defaultValue = "today") String range) {
        return ApiResponse.success(
                map(analysis.analyze(user(request).userId(), range)),
                TraceContextHolder.currentOrNew());
    }

    private NutritionAnalysisResponse map(NutritionAnalysisService.Analysis value) {
        return new NutritionAnalysisResponse(
                value.range(),
                value.from(),
                value.to(),
                value.totalItems(),
                value.matchedItems(),
                value.coverage(),
                value.caloriesKcal(),
                value.proteinG(),
                value.fatG(),
                value.carbsG(),
                value.calorieTarget(),
                value.proteinTarget(),
                value.incomplete(),
                List.copyOf(value.unmatchedNames()),
                value.disclaimer());
    }
}
