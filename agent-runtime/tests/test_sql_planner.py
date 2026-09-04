from unittest import TestCase

from model_provider import ModelProviderError, ModelResponse
from sql_planner import (
    DeterministicSqlPlanner,
    OpenAICompatibleSqlPlanner,
    ModelRouterSqlPlanner,
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

    def test_missing_shared_chat_route_fails_without_stub_fallback(self):
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_CONFIG_MISSING"):
            planner_from_environment({"FOODMATE_SQL_PLANNER_MODE": "local"})

    def test_invalid_model_json_fails_closed(self):
        planner = OpenAICompatibleSqlPlanner(self.Provider("not-json"), "local-model")

        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_RESPONSE_INVALID"):
            planner.plan("最近7天蛋白质摄入")


class ModelRouterSqlPlannerTests(TestCase):
    class Router:
        environment = {
            "FOODMATE_MODEL_TIER_STANDARD": "cloud_primary:chat-model",
        }

        def __init__(self, content):
            self.content = content
            self.calls = []

        def fallback_tiers_for(self, _tier):
            return ()

        def invoke(self, request, tier, fallback_tiers, governed_route=None):
            self.calls.append((request, tier, fallback_tiers, governed_route))
            return ModelResponse(self.content, 12, 10, "provider-request"), ["attempt"]

    def test_local_mode_uses_shared_chat_route_and_returns_attempts(self):
        router = self.Router(
            '{"status":"ready","intent":"nutrition_summary",'
            '"time_range":{"kind":"relative","days":"7","timezone":"Asia/Shanghai"},'
            '"metrics":["protein_g"],"dimensions":["meal_time"],"filters":{},'
            '"candidate_sql":"SELECT meal_time FROM food_logs LIMIT 500","missing_slots":[]}'
        )
        planner = planner_from_environment(
            {
                "FOODMATE_SQL_PLANNER_MODE": "local",
                "FOODMATE_MODEL_TIER_STANDARD": "cloud_primary:chat-model",
            },
            router,
        )

        plan, attempts = planner.plan_with_attempts(
            "最近7天蛋白质摄入",
            governed_route={"provider_code": "cloud_primary", "model_name": "chat-model"},
        )

        self.assertIsInstance(planner, ModelRouterSqlPlanner)
        self.assertEqual("local", plan.planner_mode)
        self.assertEqual(["attempt"], attempts)
        self.assertEqual("sql_planner", router.calls[0][0].scene)
        self.assertEqual("standard", router.calls[0][1])
        self.assertEqual("cloud_primary", router.calls[0][3]["provider_code"])
        self.assertNotIn("API_KEY", router.calls[0][0].prompt)

    def test_local_mode_rejects_deterministic_route_instead_of_falling_back(self):
        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_CONFIG_MISSING"):
            planner_from_environment(
                {
                    "FOODMATE_SQL_PLANNER_MODE": "local",
                    "FOODMATE_MODEL_TIER_STANDARD": "deterministic:local",
                }
            )

    def test_governed_deterministic_route_is_rejected(self):
        router = self.Router(
            '{"status":"ready","intent":"nutrition_summary",'
            '"time_range":{"kind":"relative","days":"7"},"metrics":[],"dimensions":[],"filters":{},'
            '"candidate_sql":"SELECT meal_time FROM food_logs LIMIT 1","missing_slots":[]}'
        )
        planner = ModelRouterSqlPlanner(router, "standard", 30)

        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_CONFIG_MISSING"):
            planner.plan_with_attempts(
                "最近7天饮食",
                governed_route={"provider_code": "deterministic", "model_name": "local"},
            )

    def test_shared_provider_failure_is_stable_and_preserves_attempts(self):
        class FailingRouter(self.Router):
            def invoke(self, *_args, **_kwargs):
                error = ModelProviderError("MODEL_PROVIDER_REJECTED", "provider rejected")
                error.attempts = ["attempt"]
                raise error

        planner = ModelRouterSqlPlanner(FailingRouter(""), "standard", 30)

        with self.assertRaisesRegex(SqlPlannerError, "SQL_PLANNER_MODEL_UNAVAILABLE") as raised:
            planner.plan_with_attempts("最近7天饮食")

        self.assertEqual(["attempt"], raised.exception.attempts)
