package org.feiesos.asynctaskhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessServiceTest {

    private final ImageProcessService service = new ImageProcessService();

    @TempDir
    Path tempDir;

    @Test
    void compressImage_success() throws IOException {
        Path inputFile = tempDir.resolve("test.jpg");
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", inputFile.toFile());

        String outputPath = service.compressImage(inputFile.toString(), Map.of("quality", 50));

        assertThat(outputPath).isEqualTo(inputFile.toString().replaceAll("(\\.[^.]+)$", "_compressed$1"));
        assertThat(java.nio.file.Files.exists(Path.of(outputPath))).isTrue();
    }

    @Test
    void compressImage_fileNotFound() {
        String nonExistent = tempDir.resolve("nope.jpg").toString();

        assertThatThrownBy(() -> service.compressImage(nonExistent, Map.of("quality", 80)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
