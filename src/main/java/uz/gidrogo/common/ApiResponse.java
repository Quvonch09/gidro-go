package uz.gidrogo.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    /** Mashina o'qiydigan xato kodi (masalan FARM_BLOCKED). Faqat xato javoblarida. */
    private String code;
    private String message;
    private T data;
    /** Maydon darajasidagi xatolar. Faqat validatsiya xatolarida. */
    private List<FieldErrorItem> fieldErrors;
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorItem {
        private String field;
        private String message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldErrorItem> fieldErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors)
                .build();
    }
}
