package com.smartqa.common.error;

import com.smartqa.clarification.ClarificationRequiredException;
import com.smartqa.common.api.ApiResponse;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex) {
        TraceLogger.warn("CONTROLLER", "ERROR", ex.getMessage(), TraceMeta.of(
                "exceptionType", ex.getClass().getSimpleName(),
                "errorCode", ex.errorCode().name()
        ));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ex.errorCode().name()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(ConflictException ex) {
        TraceLogger.warn("CONTROLLER", "ERROR", ex.getMessage(), TraceMeta.of(
                "exceptionType", ex.getClass().getSimpleName(),
                "errorCode", ex.errorCode().name()
        ));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ex.errorCode().name()));
    }

    @ExceptionHandler(ClarificationRequiredException.class)
    public ResponseEntity<ApiResponse<Object>> clarification(ClarificationRequiredException ex) {
        TraceLogger.warn("CONTROLLER", "ERROR", ex.getMessage(), TraceMeta.of(
                "exceptionType", ex.getClass().getSimpleName(),
                "errorCode", ex.errorCode().name(),
                "clarificationId", ex.clarificationId() == null ? "" : ex.clarificationId().toString()
        ));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(
                        false,
                        ex.getMessage(),
                        java.util.Map.of(
                                "clarificationId", ex.clarificationId() == null ? "" : ex.clarificationId().toString(),
                                "reason", "TARGET_AMBIGUOUS",
                                "status", "WAITING_FOR_CLARIFICATION",
                                "candidates", ex.candidates()
                        ),
                        ErrorCode.CLARIFICATION_REQUIRED.name(),
                        java.time.Instant.now()
                ));
    }

    @ExceptionHandler(SmartQaException.class)
    public ResponseEntity<ApiResponse<Void>> smartQa(SmartQaException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case VALIDATION_FAILED, INTENT_INVALID, INTENT_VALIDATION_ERROR, AI_INTENT_ERROR, CLARIFICATION_REQUIRED -> HttpStatus.BAD_REQUEST;
            case QUALITY_GATE_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case ELEMENT_NOT_FOUND, TARGET_NOT_PRESENT, VISUAL_TARGET_PRESENT_DOM_UNRESOLVED,
                    AI_IDENTIFIED_LIVE_VERIFICATION_FAILED, AMBIGUOUS_ELEMENT, LOCATOR_NOT_FOUND, LOCATOR_INVALID, LOCATOR_FAILURE, ACTIONABILITY_FAILURE,
                    ACTION_ELEMENT_MISMATCH, CONTROL_CLASSIFICATION_FAILURE, FILTER_APPLICATION_FAILURE,
                    FILTER_VALIDATION_FAILURE, SEARCH_STATE_MISMATCH, LOCATION_STATE_MISMATCH,
                    FILTER_STATE_MISMATCH, QUANTITY_STATE_MISMATCH, CART_STATE_MISMATCH, ASSERTION_FAILED,
                    BUSINESS_STATE_MISMATCH, LOGIN_STATE_FAILURE, WRONG_PAGE, RECOVERY_EXHAUSTED, STALE_ELEMENT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case AI_PROVIDER_ERROR, AI_RESPONSE_INVALID, OLLAMA_ERROR, MCP_ERROR -> HttpStatus.BAD_GATEWAY;
            case AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AI_PROVIDERS_UNAVAILABLE, AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case EXECUTION_TIMEOUT, WAIT_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case NAVIGATION_FAILURE, NETWORK_FAILURE, APPLICATION_ERROR, BROWSER_ERROR,
                    EXECUTION_FAILED, GENERATION_ERROR, VALIDATION_ERROR, ENVIRONMENT_ERROR -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND, CONFLICT, INTERNAL_ERROR, EXECUTION_STOPPED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        TraceLogger.error("CONTROLLER", "ERROR", ex.getMessage(), ex, null, TraceMeta.of(
                "errorCode", ex.errorCode().name(),
                "httpStatus", status.value()
        ));
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ex.getMessage(), ex.errorCode().name()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> bind(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().isEmpty()
                ? "Request validation failed"
                : ex.getFieldErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(message, ErrorCode.VALIDATION_FAILED.name()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiResponse<Void>> input(ServerWebInputException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Invalid request body", ErrorCode.VALIDATION_FAILED.name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.VALIDATION_FAILED.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unhandled(Exception ex) {
        log.error("Unhandled error", ex);
        TraceLogger.error("CONTROLLER", "ERROR", "Unhandled error", ex, null, TraceMeta.of(
                "httpStatus", HttpStatus.INTERNAL_SERVER_ERROR.value()
        ));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Internal server error", ErrorCode.INTERNAL_ERROR.name()));
    }
}
