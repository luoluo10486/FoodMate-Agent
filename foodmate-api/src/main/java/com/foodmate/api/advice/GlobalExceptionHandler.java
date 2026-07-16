package com.foodmate.api.advice;

import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.trace.TraceContextHolder;
import com.foodmate.shared.runtime.RuntimeException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一处理 Web 层异常并返回标准错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return failure(ErrorCode.INVALID_ARGUMENT, ErrorCode.INVALID_ARGUMENT.defaultMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return failure(ErrorCode.INVALID_ARGUMENT, ErrorCode.INVALID_ARGUMENT.defaultMessage(), details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return failure(
                ErrorCode.INVALID_ARGUMENT,
                "缺少必要参数",
                Map.of("parameter", exception.getParameterName())
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return failure(exception.errorCode(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return failure(ErrorCode.INVALID_ARGUMENT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException exception) {
        ErrorCode code = switch (exception.code()) {
            case "RUNTIME_AUTH_INVALID" -> ErrorCode.UNAUTHORIZED;
            case "RUNTIME_DEADLINE_EXCEEDED" -> ErrorCode.AGENT_TIMEOUT;
            case "RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT", "RUNTIME_CANCEL_IDEMPOTENCY_CONFLICT", "RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", "RUNTIME_STATE_CONFLICT", "RUNTIME_EVENT_GAP", "RUNTIME_EVENT_OUT_OF_ORDER" -> ErrorCode.CONFLICT;
            case "RUNTIME_UNAVAILABLE" -> ErrorCode.TOOL_FAILED;
            default -> ErrorCode.INVALID_ARGUMENT;
        };
        return failure(code, exception.getMessage(), Map.of("runtime_code", exception.code()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        var traceContext = TraceContextHolder.currentOrNew();
        log.error(
                "Unhandled exception request_id={} trace_id={}",
                traceContext.requestId(),
                traceContext.traceId(),
                exception
        );
        return failure(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), Map.of());
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode code, String message, Map<String, Object> details) {
        HttpStatus status = HttpStatus.resolve(code.httpStatus());
        ApiResponse<Void> response = ApiResponse.failure(
                code,
                message,
                details,
                TraceContextHolder.currentOrNew()
        );
        return ResponseEntity.status(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status).body(response);
    }
}
