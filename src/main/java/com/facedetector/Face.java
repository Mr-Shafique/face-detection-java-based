package com.facedetector;

import java.util.List;

/**
 * A face rectangle produced by the detector, in original image coordinates,
 * along with optional detection confidence and landmark points.
 */
public record Face(
        int x,
        int y,
        int width,
        int height,
        float confidence,
        List<FaceLandmark> landmarks) {

    public Face(int x, int y, int width, int height) {
        this(x, y, width, height, 1.0f, List.of());
    }

    public Face(int x, int y, int width, int height, float confidence) {
        this(x, y, width, height, confidence, List.of());
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    /**
     * Radius large enough for a circle to contain the whole face rectangle.
     */
    public int radius() {
        return (int) Math.ceil(Math.hypot(width, height) / 2.0 * 1.05);
    }
}
