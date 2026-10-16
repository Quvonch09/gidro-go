package uz.gidrogo.modules.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.BadRequestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "Fayl yuklash API (rasm, hujjat)")
public class FileController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    /** Ruxsat etilgan MIME turi → saqlanadigan kengaytma (asl fayl nomidagi kengaytmaga ishonilmaydi). */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/jpg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif"
    );

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Rasm yuklash (multipart/form-data) — URL qaytaradi")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @RequestPart("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fayl tanlanmagan");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType.toLowerCase())) {
            throw new BadRequestException("Faqat rasm fayllar qabul qilinadi (JPEG, PNG, WEBP, GIF)");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Fayl hajmi 10 MB dan oshmasligi kerak");
        }

        // Papka tuzilmasi: uploads/YYYY/MM/DD/
        LocalDate today = LocalDate.now();
        String subDir = today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + String.format("%02d", today.getDayOfMonth());
        Path uploadPath = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // Noyob fayl nomi
        String ext = ALLOWED_TYPES.get(contentType.toLowerCase());
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        Path filePath = uploadPath.resolve(fileName).toAbsolutePath().normalize();
        file.transferTo(filePath);

        // BE-13: nisbiy yo'l — HTTPS sahifada mixed-content bo'lmaydi, domen o'zgarsa ham ishlaydi
        String fileUrl = "/api/files/view/" + subDir + "/" + fileName;
        log.info("Fayl yuklandi: {}", fileUrl);

        return ResponseEntity.ok(ApiResponse.ok("Fayl muvaffaqiyatli yuklandi",
                Map.of("url", fileUrl, "fileName", fileName)));
    }

    @GetMapping("/view/{year}/{month}/{day}/{fileName}")
    @Operation(summary = "Yuklangan rasmni ko'rish (statik fayl xizmati)")
    public ResponseEntity<byte[]> viewFile(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String day,
            @PathVariable String fileName) throws IOException {

        Path filePath = Paths.get(uploadDir, year, month, day, fileName);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        byte[] content = Files.readAllBytes(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }
}
