package com.watcher.capture;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScreenCaptureTest {

    @Test
    public void testIsBlackFrameWithPureBlack() {
        BufferedImage blackImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        // Default new BufferedImage has all pixels initialized to RGB (0, 0, 0)
        assertTrue(ScreenCapture.isBlackFrame(blackImage), "Pure black image must be identified as black frame");
    }

    @Test
    public void testIsBlackFrameWithNullImage() {
        assertTrue(ScreenCapture.isBlackFrame(null), "Null image must be treated as black frame");
    }

    @Test
    public void testIsBlackFrameWithNearBlackNoise() {
        BufferedImage nearBlack = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nearBlack.createGraphics();
        g.setColor(new Color(5, 5, 5));
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        assertTrue(ScreenCapture.isBlackFrame(nearBlack), "Near-black noise (rgb 5,5,5) should be detected as black frame");
    }

    @Test
    public void testIsBlackFrameWithColoredDesktop() {
        BufferedImage desktop = new BufferedImage(1366, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = desktop.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 1366, 768);
        g.setColor(Color.BLUE);
        g.fillRect(100, 100, 500, 400);
        g.dispose();

        assertFalse(ScreenCapture.isBlackFrame(desktop), "Normal desktop with content must not be identified as black frame");
    }

    @Test
    public void testIsBlackFrameWithSmallBrightWindow() {
        BufferedImage mostlyBlack = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mostlyBlack.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(300, 300, 200, 200);
        g.dispose();

        assertFalse(ScreenCapture.isBlackFrame(mostlyBlack), "Image with a bright window must not be identified as black frame");
    }
}
