package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 校验 V7 USDA 营养目录扩展的来源、数量和幂等边界。 */
class NutritionExpansionV7SeedScriptTest {
    private static final Path SEED =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "seed",
                    "V7__nutrition_usda_directory_expansion_seed.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V7__nutrition_usda_directory_expansion_validation.sql");

    @Test
    void seedContainsTwentyThreeOfficialFoodsAndPortions() throws Exception {
        String sql = Files.readString(SEED);

        assertEquals(23, count(sql, "USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-"));
        assertEquals(23, count(sql, "USDA FoodData Central API foodPortions"));
        assertTrue(sql.contains("ON CONFLICT (nutrition_food_id) DO NOTHING"));
        assertTrue(sql.contains("ON CONFLICT (conversion_id) DO NOTHING"));
        assertTrue(sql.contains("FDC-167762"));
        assertTrue(sql.contains("FDC-172430"));
    }

    @Test
    void validationCoversCountsSourceAndForeignKeyIntegrity() throws Exception {
        String sql = Files.readString(VALIDATION);

        assertTrue(sql.contains("nutrition_food_id BETWEEN 510026 AND 510048"));
        assertTrue(sql.contains("conversion_id BETWEEN 520026 AND 520048"));
        assertTrue(sql.contains("expansion_nutrition_seed_rows"));
        assertTrue(sql.contains("expansion_unit_conversion_seed_rows"));
        assertTrue(sql.contains("expansion_conversion_food_mismatch_rows"));
        assertTrue(sql.contains("USDA FoodData Central API foodPortions"));
    }

    private static int count(String value, String fragment) {
        return (int) Pattern.compile(Pattern.quote(fragment)).matcher(value).results().count();
    }
}
