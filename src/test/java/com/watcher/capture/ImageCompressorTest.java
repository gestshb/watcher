package com.watcher.capture;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ImageCompressorTest {

    @Test
    public void testCompressToJpegProducesValidOutput() throws IOException {
        int width = 800;
        int height = 600;
        BufferedImage testImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Watcher Unit Test", 100, 100);
        g2d.dispose();

        byte[] highQualityBytes = ImageCompressor.compressToJpeg(testImage, 0.9f);
        byte[] lowQualityBytes = ImageCompressor.compressToJpeg(testImage, 0.2f);

        assertNotNull(highQualityBytes);
        assertNotNull(lowQualityBytes);
        assertTrue(highQualityBytes.length > 0);
        assertTrue(lowQualityBytes.length > 0);

        // Lower quality should generally result in smaller or comparable file size
        assertTrue(lowQualityBytes.length <= highQualityBytes.length);
    }

    @Test
    public void testCompressNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ImageCompressor.compressToJpeg(null, 0.6f));
    }
}
