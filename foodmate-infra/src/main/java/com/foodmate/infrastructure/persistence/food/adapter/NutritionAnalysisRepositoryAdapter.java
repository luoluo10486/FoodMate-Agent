package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.food.port.out.NutritionAnalysisRepository;
import com.foodmate.infrastructure.persistence.food.NutritionAnalysisMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将营养分析 SQL 聚合暴露为 application 端口。 */
@Repository
@Profile("local")
public class NutritionAnalysisRepositoryAdapter implements NutritionAnalysisRepository {
    private final NutritionAnalysisMapper mapper;

    public NutritionAnalysisRepositoryAdapter(NutritionAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NutrientAggregate aggregate(long userId, Instant from, Instant to) {
        return mapper.aggregate(userId, from, to);
    }

    @Override
    public List<String> unmatchedNames(long userId, Instant from, Instant to) {
        return mapper.unmatchedNames(userId, from, to);
    }

    @Override
    public Targets findTargets(long userId) {
        return mapper.findTargets(userId);
    }
}
