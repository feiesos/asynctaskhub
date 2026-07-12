package org.feiesos.asynctaskhub.controller;

import lombok.RequiredArgsConstructor;
import org.feiesos.asynctaskhub.common.ApiResponse;
import org.feiesos.asynctaskhub.common.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "file is required");
        }

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "upload");
        String normalizedFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = UUID.randomUUID() + "_" + normalizedFilename;

        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        Path destination = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        String filePath = destination.toAbsolutePath().normalize().toString().replace('\\', '/');
        return ApiResponse.ok(Map.of("filePath", filePath));
    }
}
