package org.feiesos.asynctaskhub.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class ImageProcessService {

    private static final int DEFAULT_QUALITY = 80;

    public String compressImage(String filePath, Map<String, Object> params) {
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("Input file not found: " + filePath);
        }

        int quality = DEFAULT_QUALITY;
        if (params != null && params.get("quality") instanceof Number n) {
            quality = n.intValue();
        }

        String outputPath = filePath.replaceAll("(\\.[^.]+)$", "_compressed$1");
        File outputFile = new File(outputPath);

        try {
            Thumbnails.of(inputFile)
                    .scale(1.0)
                    .outputQuality(quality / 100.0)
                    .toFile(outputFile);
            log.info("Image compressed: input={}, output={}, quality={}", filePath, outputPath, quality);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress image: " + filePath, e);
        }

        return outputPath;
    }
}
