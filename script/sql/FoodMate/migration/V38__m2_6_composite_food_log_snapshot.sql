-- M2-6 复合菜饮食记录聚合营养快照约束修正。
-- 复合菜不是营养目录原子食材，饮食明细使用 composite_dish:<id> 快照来源。
-- 仅放行这种可验证的聚合项，不改变普通食材 matched 明细的约束。

BEGIN;

ALTER TABLE food_log_items
    DROP CONSTRAINT IF EXISTS chk_food_log_items_matched_snapshot;

ALTER TABLE food_log_items
    ADD CONSTRAINT chk_food_log_items_matched_snapshot CHECK (
        nutrition_status <> 'matched'
        OR (
            normalized_amount IS NOT NULL
            AND calories_kcal IS NOT NULL
            AND protein_g IS NOT NULL
            AND fat_g IS NOT NULL
            AND carbs_g IS NOT NULL
            AND (
                (
                    nutrition_food_id IS NOT NULL
                    AND normalized_unit IN ('g', 'ml')
                )
                OR (
                    nutrition_food_id IS NULL
                    AND normalized_unit = 'serving'
                    AND nutrition_source ~ '^composite_dish:[0-9]+$'
                    AND nutrition_version ~ '^revision:[0-9]+$'
                )
            )
        )
    );

COMMENT ON CONSTRAINT chk_food_log_items_matched_snapshot ON food_log_items IS
    '普通食材 matched 必须关联营养目录；复合菜 matched 允许使用可验证的聚合营养快照。';

COMMIT;
