package com.foodmate.application.food.service;

import java.util.Locale;

/** 统一饮食记录和复合菜的中文份量单位，避免同一单位在不同写入路径产生不同结果。 */
public final class NutritionUnitNormalizer {
    private NutritionUnitNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "克", "g", "gram", "grams" -> "g";
            case "公斤", "千克", "kg", "kilogram", "kilograms" -> "kg";
            case "毫克", "mg", "milligram", "milligrams" -> "mg";
            case "毫升", "ml", "milliliter", "milliliters" -> "milliliter";
            case "升", "l", "liter", "liters", "litre", "litres" -> "liter";
            case "杯", "cup", "cups" -> "cup";
            case "份", "一份", "serving" -> "serving";
            case "个", "枚", "件", "piece" -> "piece";
            case "片", "slice" -> "slice";
            case "根", "茎", "stalk" -> "stalk";
            case "叶", "leaf" -> "leaf";
            case "头", "head" -> "head";
            case "束", "把", "bunch" -> "bunch";
            case "大号", "大个", "large" -> "large";
            case "中号", "中等", "medium" -> "medium";
            case "小号", "小个", "small" -> "small";
            case "盎司", "oz" -> "oz";
            case "磅", "lb", "lbs" -> "lb";
            case "汤匙", "大匙", "大勺", "tbsp", "tablespoon" -> "tablespoon";
            case "茶匙", "小匙", "小勺", "tsp", "teaspoon" -> "teaspoon";
            default -> value.trim().toLowerCase(Locale.ROOT);
        };
    }
}
