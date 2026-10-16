package uz.gidrogo.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yagona xato formati (BE-18): {success:false, code, message, fieldErrors, timestamp}.
 * Javobda Java klass nomlari, stack trace yoki SQL bo'lmaydi.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** "COURIER_AT_CAPACITY: matn" ko'rinishidagi eski ResponseStatusException xabarlaridan kodni ajratadi. */
    private static final Pattern CODE_PREFIX = Pattern.compile("^([A-Z][A-Z0-9_]{2,}):\\s*(.*)$", Pattern.DOTALL);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
        ApiResponse<Object> body = ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getFieldErrors());
        body.setData(ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "So'ralgan ma'lumot topilmadi", null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        String code = status.name();
        Matcher m = CODE_PREFIX.matcher(reason);
        if (m.matches()) {
            code = m.group(1);
            reason = m.group(2);
        }
        return error(status, code, reason, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu amal uchun ruxsatingiz yo'q", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String summary = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .reduce((a, b) -> a + ", " + b)
                .orElse("So'rov ma'lumotlari noto'g'ri");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", summary, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiResponse.FieldErrorItem> fields = ex.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> new ApiResponse.FieldErrorItem(
                        r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "So'rov parametrlari noto'g'ri", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiResponse.FieldErrorItem> fields = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    return new ApiResponse.FieldErrorItem(dot >= 0 ? path.substring(dot + 1) : path, v.getMessage());
                })
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "So'rov parametrlari noto'g'ri", fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        String expected = ex.getRequiredType() != null ? humanType(ex.getRequiredType()) : "to'g'ri qiymat";
        String msg = "'" + field + "' parametri " + expected + " bo'lishi kerak";
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", msg,
                List.of(new ApiResponse.FieldErrorItem(field, msg)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = "'" + ex.getParameterName() + "' parametri majburiy";
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", msg,
                List.of(new ApiResponse.FieldErrorItem(ex.getParameterName(), msg)));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadJson(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String field = null;
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife && !ife.getPath().isEmpty()) {
            field = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
        }
        String msg = field != null
                ? "'" + field + "' maydonining qiymati noto'g'ri formatda"
                : "Yuborilgan JSON formati noto'g'ri";
        return error(HttpStatus.BAD_REQUEST, "INVALID_BODY", msg,
                field != null ? List.of(new ApiResponse.FieldErrorItem(field, msg)) : null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "Bunday API endpoint mavjud emas", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Ushbu HTTP method qo'llab-quvvatlanmaydi: " + ex.getMethod(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type qo'llab-quvvatlanmaydi", null);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleLock(Exception ex) {
        log.warn("Lock conflict: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Ma'lumot bir vaqtda boshqa so'rov tomonidan o'zgartirilmoqda, qayta urinib ko'ring", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Ma'lumot mavjud yozuvlar bilan ziddiyatga kirdi", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Tizimda vaqtinchalik xatolik yuz berdi", null);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message,
                                                    List<ApiResponse.FieldErrorItem> fields) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, fields));
    }

    private String humanType(Class<?> type) {
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) return "son";
        if (type.isEnum()) {
            Object[] values = type.getEnumConstants();
            StringBuilder sb = new StringBuilder("quyidagilardan biri: ");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(values[i]);
            }
            return sb.toString();
        }
        if (java.time.temporal.Temporal.class.isAssignableFrom(type)) return "sana (YYYY-MM-DD)";
        if (type == Boolean.class) return "true yoki false";
        return "to'g'ri formatdagi qiymat";
    }
}
