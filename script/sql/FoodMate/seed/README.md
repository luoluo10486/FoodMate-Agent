# FoodMate 营养目录 Seed

本目录存放人工评审后的营养目录数据，不由 Java 启动自动执行，也不由 Flyway 自动执行。

## V1

`V1__nutrition_usda_seed.sql` 首批导入 5 条 USDA FoodData Central `SR Legacy` 数据：米饭、鸡胸肉、鸡蛋、三文鱼和苹果。所有数值都是每 100g 基准值，来源版本和 FDC ID 写入 `nutrition_foods.source_version`，并以 `approved` 状态供 Java 匹配。

本批不提供“个、碗、勺”等家庭单位换算，也不把 ml 默认当作 g。只有完成食材级别的官方份量或密度核验后，才可新增 `nutrition_unit_conversions` 记录。

官方依据：

- [USDA FoodData Central API Guide](https://fdc.nal.usda.gov/api-guide.html)：API、数据类型、许可证和建议引用。
- [USDA FoodData Central Data Documentation](https://fdc.nal.usda.gov/data-documentation.html)：`SR Legacy` 数据类型说明。

## 执行顺序

1. 确认目标数据库为本地 `FoodMate`，并先读取 seed SQL 和对应校验 SQL。
2. 人工执行 `V1__nutrition_usda_seed.sql`。
3. 执行 `validation/V1__nutrition_usda_seed_validation.sql`，确认 5 条记录为 `approved`、基准单位为 `g`、FDC ID 和四项营养值齐全。
4. 用真实 HTTP 创建带 `rice`、`鸡胸肉` 或其他已覆盖别名的饮食记录，确认明细为 `matched`，再验证营养分析。

seed 可重复执行：相同 `nutrition_food_id` 会被跳过；如果同一标准名称已被其他 ID 占用，SQL 会失败，必须先做数据评审，不得静默覆盖目录。
