import unittest
from datetime import datetime, timezone

from agent_core import Context, ContextBuilder, RouteDecision, generate_memory_candidates


class MemoryContextTest(unittest.TestCase):
    def context_for(self, content: str) -> Context:
        return Context(
            messages=(
                {"message_id": "assistant-1", "role": "assistant", "content": "好的"},
                {"message_id": "message-1", "role": "user", "content": content},
            ),
            summary=None,
            memories=(),
            unresolved_slots=(),
            sources={"message_id": ("assistant-1", "message-1")},
        )

    def test_extracts_stable_preferences_into_structured_candidates(self):
        content = "我喜欢清淡，我不吃香菜，我的预算是每天80元，我通常晚上7点吃饭，我不会做饭，以后回答请简洁"

        candidates = generate_memory_candidates(self.context_for(content), content, max_candidates=6)

        self.assertEqual(
            {
                ("preference", "diet_style"),
                ("constraint", "avoid_foods"),
                ("budget_habit", "daily_budget"),
                ("time_habit", "meal_time"),
                ("cooking_skill", "self_reported_level"),
                ("interaction_preference", "response_style"),
            },
            {(item["memory_type"], item["memory_key"]) for item in candidates},
        )
        self.assertTrue(all(item["source_message_ids"] == ["message-1"] for item in candidates))
        self.assertTrue(all(len(item["memory_value"]) == 1 for item in candidates))

    def test_does_not_turn_one_shot_request_or_health_fact_into_memory(self):
        one_shot_requests = (
            "今天请给我安排一份低脂晚餐计划",
            "这周晚餐吃什么",
            "今天晚餐不要放香菜",
        )
        health = "我有糖尿病，营养目标是每天摄入1200卡"

        for one_shot in one_shot_requests:
            with self.subTest(content=one_shot):
                self.assertEqual([], generate_memory_candidates(self.context_for(one_shot), one_shot))
        self.assertEqual([], generate_memory_candidates(self.context_for(health), health))

    def test_extracts_natural_stable_preferences_and_limits(self):
        examples = (
            "我不太喜欢香菜",
            "我更倾向于清淡饮食",
            "平时我喜欢吃鱼",
            "我的预算每天不超过 80 元",
            "每餐预算控制在 30 元以内",
            "我只会做简单的家常菜",
            "之后请用中文回答",
        )

        candidates = [
            candidate
            for content in examples
            for candidate in generate_memory_candidates(
                self.context_for(content), content, max_candidates=6
            )
        ]

        by_key = {(item["memory_type"], item["memory_key"]): item for item in candidates}
        preference_values = {
            item["memory_value"]["preference"]
            for item in candidates
            if (item["memory_type"], item["memory_key"]) == ("preference", "diet_style")
        }
        self.assertEqual("香菜", by_key[("constraint", "avoid_foods")]["memory_value"]["foods"])
        self.assertEqual({"清淡饮食", "吃鱼"}, preference_values)
        self.assertEqual("80 元", by_key[("budget_habit", "daily_budget")]["memory_value"]["amount"])
        self.assertEqual("30 元", by_key[("budget_habit", "meal_budget")]["memory_value"]["amount"])
        self.assertIn(
            "我只会做简单的家常菜",
            by_key[("cooking_skill", "self_reported_level")]["memory_value"]["description"],
        )
        self.assertEqual(
            "用中文", by_key[("interaction_preference", "response_style")]["memory_value"]["style"]
        )

    def test_context_filters_memory_types_status_and_duplicate_keys(self):
        command = {
            "authorized_context": {
                "long_term_memories": [
                    {"memory_id": "m1", "memory_type": "preference", "memory_key": "diet"},
                    {"memory_id": "m2", "memory_type": "plan", "memory_key": "week"},
                    {"memory_id": "m3", "memory_type": "preference", "memory_key": "diet"},
                    {
                        "memory_id": "m4",
                        "memory_type": "constraint",
                        "memory_key": "avoid_foods",
                        "confirmation_status": "conflict",
                    },
                ]
            }
        }

        context = ContextBuilder().build(command, RouteDecision("planning", "simple", "low"))

        self.assertEqual(("m1",), context.sources["memory_id"])
        self.assertEqual(("m1",), tuple(item["memory_id"] for item in context.memories))

    def test_context_excludes_deleted_expired_and_invalid_memories(self):
        command = {
            "authorized_context": {
                "long_term_memories": [
                    {"memory_id": "active", "memory_type": "preference", "memory_key": "diet"},
                    {
                        "memory_id": "deleted",
                        "memory_type": "preference",
                        "memory_key": "deleted",
                        "is_deleted": True,
                    },
                    {
                        "memory_id": "expired",
                        "memory_type": "preference",
                        "memory_key": "expired",
                        "expires_at": "2026-09-06T23:59:59Z",
                    },
                    {
                        "memory_id": "invalid-time",
                        "memory_type": "preference",
                        "memory_key": "invalid-time",
                        "expires_at": "not-a-timestamp",
                    },
                ]
            }
        }

        context = ContextBuilder(
            now_provider=lambda: datetime(2026, 9, 7, tzinfo=timezone.utc)
        ).build(command, RouteDecision("planning", "simple", "low"))

        self.assertEqual(("active",), context.sources["memory_id"])

    def test_context_keeps_memory_until_expiration_boundary(self):
        command = {
            "authorized_context": {
                "long_term_memories": [
                    {
                        "memory_id": "future",
                        "memory_type": "preference",
                        "memory_key": "diet",
                        "expires_at": "2026-09-07T00:00:01Z",
                    }
                ]
            }
        }

        context = ContextBuilder(
            now_provider=lambda: datetime(2026, 9, 7, tzinfo=timezone.utc)
        ).build(command, RouteDecision("planning", "simple", "low"))

        self.assertEqual(("future",), context.sources["memory_id"])

    def test_source_falls_back_to_latest_context_source_for_legacy_message_shape(self):
        content = "我喜欢清淡"
        context = Context(
            messages=({"message_id": "legacy-message", "content": content},),
            summary=None,
            memories=(),
            unresolved_slots=(),
            sources={"message_id": ("legacy-message",)},
        )

        candidates = generate_memory_candidates(context, content)

        self.assertEqual(["legacy-message"], candidates[0]["source_message_ids"])


if __name__ == "__main__":
    unittest.main()
