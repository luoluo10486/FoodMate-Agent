package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 校验 V5 常见食材目录和 USDA 份量规则的来源与幂等边界。 */
class NutritionCommonV5SeedScriptTest {
    private static final Path SEED =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "seed",
                    "V5__nutrition_usda_common_foods_seed.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V5__nutrition_usda_common_foods_validation.sql");

    @Test
    void seedContainsReviewedFoodsAndFoodDataCentralIdentifiers() throws IOException {
        String sql = Files.readString(SEED);

        assertTrue(sql.contains("USDA FoodData Central SR Legacy"));
        assertTrue(sql.contains("FDC-168462"));
        assertTrue(sql.contains("FDC-168483"));
        assertTrue(sql.contains("FDC-169097"));
        assertTrue(sql.contains("FDC-170393"));
        assertTrue(sql.contains("FDC-170457"));
        assertTrue(sql.contains("FDC-171284"));
        assertTrue(sql.contains("FDC-171705"));
        assertTrue(sql.contains("FDC-171971"));
        assertTrue(sql.contains("FDC-173757"));
        assertTrue(sql.contains("510017"));
        assertTrue(sql.contains("510025"));
        assertTrue(sql.contains("ON CONFLICT (nutrition_food_id) DO NOTHING"));
    }

    @Test
    void seedContainsOnlyFoodPortionDerivedConversions() throws IOException {
        String sql = Files.readString(SEED);

        assertTrue(sql.contains("USDA FoodData Central API foodPortions"));
        assertTrue(sql.contains("520017"));
        assertTrue(sql.contains("520025"));
        assertTrue(sql.contains("FDC-171971 portion-1 (3 oz=85g norm)"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-170457 portion-2"));
        assertTrue(sql.contains("ON CONFLICT (conversion_id) DO UPDATE"));
    }

    @Test
    void validationChecksApprovedRowsAndFoodReferences() throws IOException {
        String sql = Files.readString(VALIDATION);

        assertTrue(sql.contains("common_nutrition_seed_rows"));
        assertTrue(sql.contains("invalid_common_nutrition_seed_rows"));
        assertTrue(sql.contains("common_unit_conversion_seed_rows"));
        assertTrue(sql.contains("invalid_common_unit_conversion_seed_rows"));
        assertTrue(sql.contains("common_conversion_food_mismatch_rows"));
        assertTrue(sql.contains("review_status <> 'approved'"));
    }

    @Test
    void sourceVersionValuesFitTheDatabaseColumn() throws IOException {
        String sql = Files.readString(SEED);
        Matcher matcher = Pattern.compile("'([^']*FDC-[^']*)'").matcher(sql);

        while (matcher.find()) {
            assertTrue(
                    matcher.group(1).length() <= 64,
                    () -> "source_version exceeds VARCHAR(64): " + matcher.group(1));
        }
    }
}
