package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.foodmate.application.food.service.NutritionUnitNormalizer;
import org.junit.jupiter.api.Test;

/** 中文份量单位统一规则测试，保证饮食记录和复合菜共享同一语义。 */
class NutritionUnitNormalizerTest {
    @Test
    void normalizesCommonChineseUnitsToCatalogUnits() {
        assertEquals("g", NutritionUnitNormalizer.normalize("克"));
        assertEquals("serving", NutritionUnitNormalizer.normalize("份"));
        assertEquals("piece", NutritionUnitNormalizer.normalize("枚"));
        assertEquals("slice", NutritionUnitNormalizer.normalize("片"));
        assertEquals("cup", NutritionUnitNormalizer.normalize("杯"));
        assertEquals("tablespoon", NutritionUnitNormalizer.normalize("大勺"));
    }

    @Test
    void keepsUnknownUnitExplicitInsteadOfGuessing() {
        assertEquals("碗", NutritionUnitNormalizer.normalize("碗"));
    }
}
