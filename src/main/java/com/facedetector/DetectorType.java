package com.facedetector;

/**
 * Available face detection engines.
 */
public enum DetectorType {
    YUNET("Deep Learning AI (YuNet) - Groups & Profiles"),
    HAAR_CASCADE("Haar Cascade (Legacy Frontal)");

    private final String displayName;

    DetectorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
