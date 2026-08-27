package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodmate.application.runtime.service.CalculatorEvaluator;
import org.junit.jupiter.api.Test;

class CalculatorEvaluatorTest {
    @Test
    void evaluatesBoundedArithmeticWithPrecedence() {
        var result = CalculatorEvaluator.evaluate("(20 * 1.1) + 5 / 2");

        assertTrue(result.succeeded());
        assertEquals("24.5", result.value().toPlainString());
    }

    @Test
    void rejectsCodeLikeAndUnboundedExpressions() {
        assertEquals(
                "CALCULATOR_EXPRESSION_INVALID",
                CalculatorEvaluator.evaluate("1; Runtime.getRuntime()").errorCode());
        assertEquals(
                "CALCULATOR_DIVISION_BY_ZERO", CalculatorEvaluator.evaluate("1 / 0").errorCode());
        assertEquals(
                "CALCULATOR_RESULT_OUT_OF_BOUNDS",
                CalculatorEvaluator.evaluate("1000000000001 * 2").errorCode());
    }
}
