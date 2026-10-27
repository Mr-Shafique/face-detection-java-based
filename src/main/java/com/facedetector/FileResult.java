package com.facedetector;

import java.nio.file.Path;

public record FileResult(Path input, Path output, int faceCount, String error) {
    public boolean succeeded() {
        return error == null;
    }
}
