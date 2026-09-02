package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 校验 V8 USDA 营养目录扩展的来源、数量、份量和幂等边界。 */
class NutritionExpansionV8SeedScriptTest {
    private static final Path SEED =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "seed",
                    "V8__nutrition_usda_directory_expansion_seed.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V8__nutrition_usda_directory_expansion_validation.sql");

    @Test
    void seedContainsTwelveOfficialFoodsAndPortions() throws Exception {
        String sql = Files.readString(SEED);

        assertEquals(12, count(sql, "USDA FoodData Central', 'SR Legacy 2019-04-01 FDC-"));
        assertEquals(12, count(sql, "USDA FoodData Central API foodPortions"));
        assertTrue(sql.contains("FDC-170000"));
        assertTrue(sql.contains("FDC-175168"));
        assertTrue(sql.contains("FDC-173734"));
        assertTrue(sql.contains("FDC-170894"));
        assertTrue(sql.contains("ON CONFLICT (nutrition_food_id) DO UPDATE SET"));
        assertTrue(sql.contains("updated_at = CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("ON CONFLICT (conversion_id) DO NOTHING"));
    }

    @Test
    void validationCoversCountsSourceAndForeignKeyIntegrity() throws Exception {
        String sql = Files.readString(VALIDATION);

        assertTrue(sql.contains("nutrition_food_id BETWEEN 510049 AND 510060"));
        assertTrue(sql.contains("conversion_id BETWEEN 520049 AND 520060"));
        assertTrue(sql.contains("expansion_v8_nutrition_seed_rows"));
        assertTrue(sql.contains("expansion_v8_unit_conversion_seed_rows"));
        assertTrue(sql.contains("expansion_v8_conversion_food_mismatch_rows"));
        assertTrue(sql.contains("USDA FoodData Central API foodPortions"));
        assertTrue(sql.contains("conversion_id IN (520052, 520055, 520056)"));
    }

    private static int count(String value, String fragment) {
        return (int) Pattern.compile(Pattern.quote(fragment)).matcher(value).results().count();
    }
}
