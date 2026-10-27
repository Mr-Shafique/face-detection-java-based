package com.facedetector;

/**
 * A detected facial landmark coordinate (e.g. eye, nose tip, mouth corner).
 */
public record FaceLandmark(float x, float y, String name) {

    public FaceLandmark(float x, float y) {
        this(x, y, "");
    }
}
