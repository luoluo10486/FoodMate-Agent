package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M1-5 首批营养目录 seed 的来源、精度和人工执行边界。 */
class NutritionSeedScriptTest {
    private static final Path SEED =
            Path.of("..", "script", "sql", "FoodMate", "seed", "V1__nutrition_usda_seed.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V1__nutrition_usda_seed_validation.sql");
    private static final Path PORTION_SEED =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "seed",
                    "V2__nutrition_usda_portion_seed.sql");
    private static final Path PORTION_VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V2__nutrition_usda_portion_seed_validation.sql");

    @Test
    void seedContainsOnlyReviewedUsdaRowsWithTraceableFdcIds() throws IOException {
        String sql = Files.readString(SEED);

        assertTrue(sql.contains("USDA FoodData Central API Guide"));
        assertTrue(sql.contains("Chinese names use PostgreSQL Unicode escapes"));
        assertTrue(sql.contains("U&'\\7C73\\996D'"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-168880"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-171477"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-173424"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-171998"));
        assertTrue(sql.contains("SR Legacy 2019-04-01 FDC-171689"));
        assertTrue(sql.contains("'approved'"));
        assertTrue(sql.contains("ON CONFLICT (nutrition_food_id) DO NOTHING"));
    }

    @Test
    void seedUsesGramBasisAndLeavesHouseholdConversionsToSeparateEvidenceSeed() throws IOException {
        String sql = Files.readString(SEED);

        assertTrue(sql.contains("Every value below is the USDA value per 100 g"));
        assertTrue(sql.contains("'g'"));
        assertTrue(sql.contains("No household-unit conversion is inferred here"));
        assertTrue(!sql.contains("INSERT INTO nutrition_unit_conversions"));
    }

    @Test
    void validationChecksSeedStatusAndUnexpectedConversions() throws IOException {
        String sql = Files.readString(VALIDATION);

        assertTrue(sql.contains("nutrition_seed_rows"));
        assertTrue(sql.contains("invalid_nutrition_seed_rows"));
        assertTrue(sql.contains("seed_unit_conversion_rows"));
        assertTrue(sql.contains("review_status <> 'approved'"));
    }

    @Test
    void portionSeedContainsOnlyTraceableUsdaFoodPortionsRules() throws IOException {
        String sql = Files.readString(PORTION_SEED);

        assertTrue(sql.contains("USDA FoodData Central API foodPortions"));
        assertTrue(sql.contains("FDC-168880 portion-1"));
        assertTrue(sql.contains("FDC-171477 portion-1"));
        assertTrue(sql.contains("FDC-173424 portion-3"));
        assertTrue(sql.contains("FDC-171998 portion-1 (3 oz=85 g)"));
        assertTrue(sql.contains("FDC-171689 portion-3"));
        assertTrue(sql.contains("520001"));
        assertTrue(sql.contains("520005"));
        assertTrue(sql.contains("ON CONFLICT (conversion_id) DO UPDATE"));
    }

    @Test
    void portionValidationChecksApprovedRulesAndSourceVersions() throws IOException {
        String sql = Files.readString(PORTION_VALIDATION);

        assertTrue(sql.contains("nutrition_unit_conversion_seed_rows"));
        assertTrue(sql.contains("invalid_nutrition_unit_conversion_seed_rows"));
        assertTrue(sql.contains("foodPortions"));
        assertTrue(sql.contains("review_status <> 'approved'"));
    }
}
