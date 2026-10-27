package com.facedetector;

/**
 * Visual styling options for rendering annotations on images.
 */
public record AnnotationOptions(boolean showConfidence, boolean showLandmarks) {

    public static final AnnotationOptions DEFAULT = new AnnotationOptions(false, false);

    public AnnotationOptions withShowConfidence(boolean show) {
        return new AnnotationOptions(show, this.showLandmarks);
    }

    public AnnotationOptions withShowLandmarks(boolean show) {
        return new AnnotationOptions(this.showConfidence, show);
    }
}
