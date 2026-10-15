import json
import sys
from pathlib import Path
from unittest import TestCase

sys.path.append(str(Path(__file__).parents[1]))

from prompt_templates import (
    PROMPT_TEMPLATE_NAMES,
    PromptTemplateError,
    load_prompt_template,
    prompt_contract_version,
    render_prompt,
)


class PromptTemplateTests(TestCase):
    def test_every_registered_prompt_has_version_and_valid_dynamic_fields(self):
        for name in PROMPT_TEMPLATE_NAMES:
            with self.subTest(name=name):
                definition = load_prompt_template(name)
                metadata = definition["metadata"]
                payload = definition["payload"]

                self.assertEqual(name, metadata["template_id"])
                self.assertTrue(metadata["template_version"])
                self.assertTrue(metadata["dynamic_fields"])
                self.assertTrue(set(metadata["dynamic_fields"]).issubset(payload))

    def test_render_replaces_only_the_declared_dynamic_fields(self):
        prompt = json.loads(
            render_prompt(
                "intent_classifier",
                user_message="帮我查最近七天的热量",
                conversation_context=[],
            )
        )

        self.assertEqual("帮我查最近七天的热量", prompt["user_message"])
        self.assertEqual([], prompt["conversation_context"])
        self.assertEqual("intent-v1", prompt["classifier_version"])
        with self.assertRaisesRegex(PromptTemplateError, "PROMPT_TEMPLATE_INPUT_INVALID"):
            render_prompt("intent_classifier", user_message="问题", conversation_context=[], extra="x")

    def test_contract_version_is_read_from_intent_template_metadata(self):
        self.assertEqual("intent-v1", prompt_contract_version("intent_classifier"))

    def test_templates_include_intent_guidance_output_contracts_and_examples(self):
        intent = load_prompt_template("intent_classifier")["payload"]
        self.assertEqual(5, len(intent["categories"]))
        self.assertGreaterEqual(len(intent["decision_examples"]), 8)
        self.assertIn("exact_root_keys", intent["output_contract"])

        sql = load_prompt_template("sql_planner")["payload"]
        self.assertEqual(7, len(sql["intent_descriptions"]))
        self.assertEqual(7, len(sql["approved_sql_examples"]))
        self.assertGreaterEqual(len(sql["plan_examples"]), 6)
        self.assertIn("exact_root_keys", sql["output_contract"])

        writer = load_prompt_template("food_log_writer")["payload"]
        meal_plan = load_prompt_template("meal_plan_generator")["payload"]
        self.assertGreaterEqual(len(writer["examples"]), 2)
        self.assertGreaterEqual(len(meal_plan["examples"]), 2)
        self.assertIn("exact_root_keys", writer["output_contract"])
        self.assertIn("exact_root_keys", meal_plan["output_contract"])
        self.assertIn("已在 needs_clarification 中列明", " ".join(writer["rules"]))
        self.assertIn("已列入 needs_clarification", writer["output_contract"]["items"])

        composer = load_prompt_template("composer")["payload"]
        evaluator = load_prompt_template("answer_evaluator")["payload"]
        self.assertEqual(6, len(composer["intent_descriptions"]))
        self.assertGreaterEqual(len(composer["few_shot_examples"]), 5)
        self.assertEqual(5, len(evaluator["intent_descriptions"]))
        self.assertGreaterEqual(len(evaluator["few_shot_examples"]), 3)
        self.assertIn("exact_root_keys", evaluator["output_contract"])
