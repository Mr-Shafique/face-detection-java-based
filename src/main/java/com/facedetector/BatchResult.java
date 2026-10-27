package com.facedetector;

import java.nio.file.Path;
import java.util.List;

public record BatchResult(Path outputDir, List<FileResult> files, long elapsedMillis) {

    public int processed() {
        return files.size();
    }

    public int totalFaces() {
        return files.stream().mapToInt(FileResult::faceCount).sum();
    }

    public int failures() {
        return (int) files.stream().filter(file -> !file.succeeded()).count();
    }
}
