package uz.gidrogo.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Kodli biznes xatosi. Javob: {success:false, code, message, fieldErrors, timestamp}.
 * 400 — tur/format, 422 — biznes validatsiyasi, 409 — ziddiyat, 403 — ruxsat yo'q.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<ApiResponse.FieldErrorItem> fieldErrors;
    /** Javobning data qismiga qo'shiladigan qo'shimcha ma'lumot (masalan rejectReason). */
    private final transient Object details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message,
                        List<ApiResponse.FieldErrorItem> fieldErrors, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
        this.details = details;
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unprocessable(String code, String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message,
                field == null ? List.of() : List.of(new ApiResponse.FieldErrorItem(field, message)), null);
    }
}
