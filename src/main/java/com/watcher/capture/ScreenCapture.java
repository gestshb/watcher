package com.watcher.capture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures screen frames across all connected display monitors using java.awt.Robot.
 */
public class ScreenCapture {
    private static final Logger logger = LogManager.getLogger(ScreenCapture.class);
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String hostname;

    public static class CapturedFrame {
        private final String filename;
        private final byte[] jpegData;
        private final int displayIndex;
        private final LocalDateTime timestamp;

        public CapturedFrame(String filename, byte[] jpegData, int displayIndex, LocalDateTime timestamp) {
            this.filename = filename;
            this.jpegData = jpegData;
            this.displayIndex = displayIndex;
            this.timestamp = timestamp;
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getJpegData() {
            return jpegData;
        }

        public int getDisplayIndex() {
            return displayIndex;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    public ScreenCapture() {
        this(resolveHostname());
    }

    public ScreenCapture(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Captures all connected displays and returns compressed JPEG frames.
     */
    public List<CapturedFrame> captureAllDisplays(float quality) throws AWTException, IOException {
        List<CapturedFrame> frames = new ArrayList<>();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        if (ge.isHeadlessInstance()) {
            logger.warn("JVM is running in headless mode. Screen capture is not possible without an interactive session.");
            return frames;
        }

        GraphicsDevice[] screens = ge.getScreenDevices();
        LocalDateTime now = LocalDateTime.now();
        String timestampStr = now.format(FILENAME_DATE_FORMAT);

        for (int i = 0; i < screens.length; i++) {
            GraphicsDevice screen = screens[i];
            Rectangle bounds = screen.getDefaultConfiguration().getBounds();
            Robot robot = new Robot(screen);

            BufferedImage screenImage = robot.createScreenCapture(bounds);
            if (isBlackFrame(screenImage)) {
                // Fallback to default desktop Robot in case device-specific Robot had coordinate offset issues
                try {
                    Robot fallbackRobot = new Robot();
                    BufferedImage fallbackImage = fallbackRobot.createScreenCapture(bounds);
                    if (!isBlackFrame(fallbackImage)) {
                        screenImage = fallbackImage;
                    }
                } catch (Exception ignored) {
                }
            }

            if (isBlackFrame(screenImage)) {
                logger.info("Display {} returned a pure black frame (session locked, asleep, or inactive). Skipping capture.", i);
                continue;
            }

            byte[] jpegBytes = ImageCompressor.compressToJpeg(screenImage, quality);

            String filename = String.format("screenshot_%s_%s_disp%d.jpg", hostname, timestampStr, i);
            frames.add(new CapturedFrame(filename, jpegBytes, i, now));
            logger.debug("Captured display {}: {} ({} bytes)", i, filename, jpegBytes.length);
        }

        return frames;
    }

    /**
     * Checks whether the captured image is completely black (e.g. locked session, disconnected desktop, or display off).
     */
    public static boolean isBlackFrame(BufferedImage image) {
        if (image == null) {
            return true;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return true;
        }

        int stepX = Math.max(1, width / 50);
        int stepY = Math.max(1, height / 50);
        int brightnessThreshold = 12;

        for (int x = 0; x < width; x += stepX) {
            for (int y = 0; y < height; y += stepY) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > brightnessThreshold || g > brightnessThreshold || b > brightnessThreshold) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getHostname() {
        return hostname;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9.-]", "_");
        } catch (Exception e) {
            return "host-" + System.getProperty("user.name", "user");
        }
    }
}
