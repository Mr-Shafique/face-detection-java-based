package com.facedetector;

import java.awt.image.BufferedImage;
import java.util.List;

public record DetectionResult(
        List<Face> faces,
        BufferedImage annotatedImage,
        long elapsedMillis,
        DetectorType detectorType) {

    public DetectionResult(List<Face> faces, BufferedImage annotatedImage, long elapsedMillis) {
        this(faces, annotatedImage, elapsedMillis, DetectorType.YUNET);
    }
}
