package com.watcher;

import com.watcher.capture.ScreenCapture;
import com.watcher.storage.LocalSpoolManager;

import java.nio.file.Path;
import java.util.List;

public class TestRun {
    public static void main(String[] args) {
        System.out.println("Starting test capture...");
        try {
            ScreenCapture capture = new ScreenCapture();
            List<ScreenCapture.CapturedFrame> frames = capture.captureAllDisplays(0.6f);
            System.out.println("Displays detected: " + frames.size());

            LocalSpoolManager spool = new LocalSpoolManager("./spool", 50 * 1024 * 1024);
            for (ScreenCapture.CapturedFrame frame : frames) {
                Path p = spool.saveFrame(frame.getFilename(), frame.getJpegData());
                System.out.println("Captured & saved: " + p.toAbsolutePath() + " (Size: " + frame.getJpegData().length + " bytes)");
            }
            System.out.println("Test capture completed successfully!");
        } catch (Exception e) {
            System.err.println("Capture failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
