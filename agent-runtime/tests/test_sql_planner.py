from unittest import TestCase

from model_provider import ModelResponse
from sql_planner import (
    DeterministicSqlPlanner,
    OpenAICompatibleSqlPlanner,
    SqlPlannerError,
    planner_from_environment,
    validate_candidate_sql,
)


class DeterministicSqlPlannerTests(TestCase):
    def setUp(self):
        self.planner = DeterministicSqlPlanner()

    def test_recent_protein_query_returns_structured_bounded_plan(self):
        plan = self.planner.plan("分析最近7天蛋白质摄入")

        self.assertEqual("ready", plan.status)
        self.assertEqual("nutrition_summary", plan.intent)
        self.assertEqual({"kind": "relative", "days": "7", "timezone": "Asia/Shanghai"}, plan.time_range)
        self.assertEqual(("protein_g",), plan.metrics)
        self.assertIn("SUM(i.protein_g) AS protein_g", plan.candidate_sql)
        self.assertTrue(plan.candidate_sql.endswith("LIMIT 500"))
        self.assertNotIn("user_id =", plan.candidate_sql)

    def test_analysis_without_time_range_requires_clarification(self):
        plan = self.planner.plan("分析我的蛋白质摄入")

        self.assertEqual("need_clarification", plan.status)
        self.assertEqual(("time_range",), plan.missing_slots)
        self.assertIsNone(plan.candidate_sql)

    def test_fixed_templates_cover_plan_shopping_and_public_food_queries(self):
        self.assertIn("FROM meal_plans", self.planner.plan("查看我的餐食计划").candidate_sql)
        self.assertIn("FROM shopping_lists", self.planner.plan("查看购物清单").candidate_sql)
        self.assertIn("review_status = 'approved'", self.planner.plan("查询食材营养目录").candidate_sql)

    def test_time_range_and_candidate_limits_fail_closed(self):
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_TIME_RANGE_INVALID"):
            self.planner.plan("分析最近120天蛋白质摄入")
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_SQL_INVALID"):
            validate_candidate_sql("DELETE FROM food_logs LIMIT 1")
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_LIMIT_REQUIRED"):
            validate_candidate_sql("SELECT meal_time FROM food_logs")


class OpenAICompatibleSqlPlannerTests(TestCase):
    class Provider:
        def __init__(self, content):
            self.content = content

        def complete(self, _model, _request):
            return ModelResponse(self.content, 12, 10)

    def test_local_mode_validates_shared_structured_plan(self):
        planner = OpenAICompatibleSqlPlanner(
            self.Provider(
                '{"status":"ready","intent":"nutrition_summary",'
                '"time_range":{"kind":"relative","days":"7","timezone":"Asia/Shanghai"},'
                '"metrics":["protein_g"],"dimensions":["meal_time"],"filters":{},'
                '"candidate_sql":"SELECT meal_time FROM food_logs LIMIT 500","missing_slots":[]}'
            ),
            "local-model",
        )

        plan = planner.plan("最近7天蛋白质摄入")

        self.assertEqual("local", plan.planner_mode)
        self.assertEqual("ready", plan.status)
        self.assertEqual(("protein_g",), plan.metrics)

    def test_missing_local_configuration_fails_without_stub_fallback(self):
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_CONFIG_MISSING"):
            planner_from_environment({"FOODMATE_SQL_PLANNER_MODE": "local"})

    def test_invalid_model_json_fails_closed(self):
        planner = OpenAICompatibleSqlPlanner(self.Provider("not-json"), "local-model")

        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_RESPONSE_INVALID"):
            planner.plan("最近7天蛋白质摄入")
