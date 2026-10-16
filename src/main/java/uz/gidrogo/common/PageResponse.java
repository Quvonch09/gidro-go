package uz.gidrogo.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Server tomonda sahifalangan ro'yxat: {content, page, size, totalElements, totalPages}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    /** Xotirada tayyorlangan ro'yxatni sahifalash (kichik ro'yxatlar uchun). */
    public static <T> PageResponse<T> ofList(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResponse.<T>builder()
                .content(all.subList(from, to))
                .page(page)
                .size(size)
                .totalElements(all.size())
                .totalPages(size == 0 ? 0 : (int) Math.ceil(all.size() / (double) size))
                .build();
    }

    /**
     * page ≥ 0, size 1–100 tekshiruvi va sort ("field,asc|desc") ni faqat ruxsat etilgan maydonlar bilan qabul qilish.
     */
    public static Pageable pageable(int page, int size, String sort, Set<String> allowedSort, Sort defaultSort) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PARAMETER", "'page' 0 yoki undan katta bo'lishi kerak");
        }
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("INVALID_PARAMETER", "'size' 1 dan 100 gacha bo'lishi kerak");
        }
        Sort s = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            if (!allowedSort.contains(field)) {
                throw ApiException.badRequest("INVALID_PARAMETER", "'sort' maydoni ruxsat etilmagan: " + field
                        + ". Ruxsat etilganlar: " + String.join(", ", allowedSort));
            }
            Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            s = Sort.by(dir, field);
        }
        return PageRequest.of(page, size, s);
    }
}
