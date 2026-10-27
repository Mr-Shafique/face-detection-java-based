package com.facedetector;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * High-level operations shared by the GUI and the command line.
 */
public final class FaceDetectionService implements AutoCloseable {

    private final FaceDetector detector;
    private AnnotationOptions annotationOptions = AnnotationOptions.DEFAULT;

    public FaceDetectionService() {
        this.detector = new FaceDetector();
    }

    public FaceDetectionService(DetectorType type, float scoreThreshold) {
        this.detector = new FaceDetector(type, scoreThreshold);
    }

    public void setDetectorType(DetectorType type) {
        detector.setDetectorType(type);
    }

    public DetectorType getDetectorType() {
        return detector.getDetectorType();
    }

    public void setScoreThreshold(float threshold) {
        detector.setScoreThreshold(threshold);
    }

    public float getScoreThreshold() {
        return detector.getScoreThreshold();
    }

    public void setAnnotationOptions(AnnotationOptions options) {
        this.annotationOptions = (options != null) ? options : AnnotationOptions.DEFAULT;
    }

    public AnnotationOptions getAnnotationOptions() {
        return annotationOptions;
    }

    public DetectionResult process(BufferedImage image) {
        long start = System.nanoTime();
        List<Face> faces = detector.detect(image);
        BufferedImage annotated = FaceAnnotator.annotate(image, faces, annotationOptions);
        long elapsedMillis = Math.round((System.nanoTime() - start) / 1_000_000.0);
        return new DetectionResult(faces, annotated, elapsedMillis, detector.getDetectorType());
    }

    public DetectionResult processFile(Path input, Path output) throws IOException {
        DetectionResult result = process(ImageUtils.read(input));
        ImageUtils.write(result.annotatedImage(), output);
        return result;
    }

    /**
     * Processes every supported image directly inside {@code folder} and writes
     * annotated copies into {@code outputDir}.
     */
    public BatchResult processFolder(Path folder, Path outputDir, IntConsumer progress)
            throws IOException {
        List<Path> images;
        try (Stream<Path> entries = Files.list(folder)) {
            images = entries
                    .filter(Files::isRegularFile)
                    .filter(ImageUtils::isSupported)
                    .sorted()
                    .toList();
        }

        Files.createDirectories(outputDir);
        List<FileResult> results = new ArrayList<>(images.size());
        long start = System.nanoTime();

        for (int i = 0; i < images.size(); i++) {
            Path input = images.get(i);
            try {
                BufferedImage image = ImageUtils.read(input);
                DetectionResult detection = process(image);
                Path output = outputDir.resolve(ImageUtils.outputName(input));
                ImageUtils.write(detection.annotatedImage(), output);
                results.add(new FileResult(input, output, detection.faces().size(), null));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                results.add(new FileResult(input, null, 0, message));
            }
            if (progress != null) {
                progress.accept((int) Math.round((i + 1) * 100.0 / images.size()));
            }
        }

        long elapsedMillis = Math.round((System.nanoTime() - start) / 1_000_000.0);
        return new BatchResult(outputDir, List.copyOf(results), elapsedMillis);
    }

    @Override
    public void close() {
        detector.close();
    }
}
