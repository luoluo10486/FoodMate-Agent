package com.foodmate.application.runtime.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** 在不调用脚本引擎的前提下计算受限算术表达式。 */
public final class CalculatorEvaluator {
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final BigDecimal MAX_ABSOLUTE_VALUE = new BigDecimal("1000000000000");
    private static final int MAX_EXPRESSION_LENGTH = 256;
    private static final int MAX_OPERATIONS = 64;

    private CalculatorEvaluator() {}

    public static Evaluation evaluate(String expression) {
        if (expression == null
                || expression.isBlank()
                || expression.length() > MAX_EXPRESSION_LENGTH)
            return Evaluation.failure("CALCULATOR_INPUT_INVALID");
        try {
            Parser parser = new Parser(expression);
            BigDecimal value = parser.parseExpression();
            parser.skipWhitespace();
            if (!parser.atEnd()) return Evaluation.failure("CALCULATOR_EXPRESSION_INVALID");
            return Evaluation.success(value.stripTrailingZeros());
        } catch (CalculationException exception) {
            return Evaluation.failure(exception.code);
        }
    }

    public record Evaluation(BigDecimal value, String errorCode) {
        public static Evaluation success(BigDecimal value) {
            return new Evaluation(value, null);
        }

        public static Evaluation failure(String errorCode) {
            return new Evaluation(null, errorCode);
        }

        public boolean succeeded() {
            return errorCode == null;
        }
    }

    private static final class Parser {
        private final String expression;
        private int position;
        private int operations;

        private Parser(String expression) {
            this.expression = expression;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                skipWhitespace();
                if (take('+')) value = add(value, parseTerm());
                else if (take('-')) value = subtract(value, parseTerm());
                else return value;
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                skipWhitespace();
                if (take('*')) value = multiply(value, parseFactor());
                else if (take('/')) value = divide(value, parseFactor());
                else if (take('%')) value = remainder(value, parseFactor());
                else return value;
            }
        }

        private BigDecimal parseFactor() {
            skipWhitespace();
            if (take('+')) return parseFactor();
            if (take('-')) return checked(parseFactor().negate(MATH_CONTEXT));
            if (take('(')) {
                BigDecimal value = parseExpression();
                skipWhitespace();
                if (!take(')')) throw invalid();
                return value;
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() {
            skipWhitespace();
            int start = position;
            boolean hasDigits = false;
            while (!atEnd() && Character.isDigit(expression.charAt(position))) {
                position++;
                hasDigits = true;
            }
            if (!atEnd() && expression.charAt(position) == '.') {
                position++;
                while (!atEnd() && Character.isDigit(expression.charAt(position))) {
                    position++;
                    hasDigits = true;
                }
            }
            if (!hasDigits) throw invalid();
            try {
                return checked(new BigDecimal(expression.substring(start, position), MATH_CONTEXT));
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private BigDecimal add(BigDecimal left, BigDecimal right) {
            return checked(left.add(right, MATH_CONTEXT));
        }

        private BigDecimal subtract(BigDecimal left, BigDecimal right) {
            return checked(left.subtract(right, MATH_CONTEXT));
        }

        private BigDecimal multiply(BigDecimal left, BigDecimal right) {
            return checked(left.multiply(right, MATH_CONTEXT));
        }

        private BigDecimal divide(BigDecimal left, BigDecimal right) {
            if (right.signum() == 0) throw new CalculationException("CALCULATOR_DIVISION_BY_ZERO");
            return checked(left.divide(right, MATH_CONTEXT));
        }

        private BigDecimal remainder(BigDecimal left, BigDecimal right) {
            if (right.signum() == 0) throw new CalculationException("CALCULATOR_DIVISION_BY_ZERO");
            return checked(left.remainder(right, MATH_CONTEXT));
        }

        private BigDecimal checked(BigDecimal value) {
            operations++;
            if (operations > MAX_OPERATIONS
                    || value.abs().compareTo(MAX_ABSOLUTE_VALUE) > 0
                    || value.scale() > 18)
                throw new CalculationException("CALCULATOR_RESULT_OUT_OF_BOUNDS");
            return value;
        }

        private boolean take(char expected) {
            if (!atEnd() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(expression.charAt(position))) position++;
        }

        private boolean atEnd() {
            return position >= expression.length();
        }

        private CalculationException invalid() {
            return new CalculationException("CALCULATOR_EXPRESSION_INVALID");
        }
    }

    private static final class CalculationException extends RuntimeException {
        private final String code;

        private CalculationException(String code) {
            this.code = code;
        }
    }
}
