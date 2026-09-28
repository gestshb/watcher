package com.watcher.capture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Compresses raw BufferedImage instances into high-efficiency JPEG byte arrays.
 */
public class ImageCompressor {
    private static final Logger logger = LogManager.getLogger(ImageCompressor.class);

    /**
     * Compresses a BufferedImage to JPEG format using specified quality (0.1 to 1.0).
     */
    public static byte[] compressToJpeg(BufferedImage image, float quality) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        if (quality < 0.1f || quality > 1.0f) {
            quality = 0.6f;
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG ImageWriter found in runtime environment");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality);

        // Convert TYPE_CUSTOM or alpha images to standard RGB if needed
        BufferedImage rgbImage;
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            rgbImage = image;
        } else {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = rgbImage.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            writer.dispose();
            return baos.toByteArray();
        }
    }
}
