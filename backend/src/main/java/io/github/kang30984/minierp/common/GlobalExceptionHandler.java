package io.github.kang30984.minierp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /**
     * 비밀번호 오류, 없는 계정, 사용 중지 계정을 모두 같은 메시지로 응답한다.
     * 응답 시간도 같아야 해서, 계정 상태 검사는 비밀번호 확인 뒤에 한다 (SecurityConfig.loginProvider).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    /**
     * 마감월 보호 트리거(assert_month_open)가 거부한 경우.
     * 서비스가 먼저 막지만, 월이 바뀌는 순간 마감과 출고·반품이 겹치면 DB에서 거부될 수 있다(V7).
     * 출고일은 트랜잭션 시작 시각이라 다시 시도하면 이번 달로 처리되므로 500 대신 409로 알린다.
     * 그 밖의 무결성 위반은 예상하지 못한 오류로 처리한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        if (cause.contains("마감된 정산월")) {
            log.warn("마감월 데이터 변경 거부: {}", cause.lines().findFirst().orElse(cause));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("CLOSED_MONTH",
                            "이미 마감된 달의 데이터라 처리하지 못했습니다. 월이 바뀌는 시점이었다면 다시 시도해 주세요."));
        }
        return handleUnexpected(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("입력값을 확인해 주세요.");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 없는 URL(404), 지원하지 않는 메서드(405) 같은 Spring MVC 표준 예외는 원래 상태 코드를 유지한다
        if (e instanceof org.springframework.web.ErrorResponse standard) {
            HttpStatusCode status = standard.getStatusCode();
            String message = status.value() == 404 ? "요청한 주소를 찾을 수 없습니다." : "요청을 처리할 수 없습니다.";
            return ResponseEntity.status(status).body(new ErrorResponse("REQUEST_FAILED", message));
        }
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
