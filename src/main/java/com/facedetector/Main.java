package com.facedetector;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.facedetector.ui.MainWindow;

/**
 * Entry point. With no arguments the Swing window opens; with arguments the
 * tool runs headless.
 *
 * <pre>
 *   java -jar face-detector.jar                         # GUI
 *   java -jar face-detector.jar photo.jpg               # single image
 *   java -jar face-detector.jar photo.jpg result.png    # single image, chosen output
 *   java -jar face-detector.jar photos/                 # batch folder
 *   java -jar face-detector.jar photos/ results/        # batch folder, chosen output
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            int exitCode = runCommandLine(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    private static int runCommandLine(String[] args) {
        if (args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return 0;
        }

        Path input = null;
        Path output = null;
        DetectorType model = DetectorType.YUNET;
        float confidence = FaceDetector.DEFAULT_SCORE_THRESHOLD;
        boolean showScore = false;
        boolean showLandmarks = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--model") && i + 1 < args.length) {
                String val = args[++i].toLowerCase();
                if (val.contains("haar")) {
                    model = DetectorType.HAAR_CASCADE;
                } else {
                    model = DetectorType.YUNET;
                }
            } else if (arg.equals("--confidence") && i + 1 < args.length) {
                try {
                    confidence = Float.parseFloat(args[++i]);
                } catch (NumberFormatException ignored) {
                }
            } else if (arg.equals("--show-score")) {
                showScore = true;
            } else if (arg.equals("--show-landmarks")) {
                showLandmarks = true;
            } else if (!arg.startsWith("--")) {
                if (input == null) {
                    input = Path.of(arg);
                } else if (output == null) {
                    output = Path.of(arg);
                }
            }
        }

        if (input == null) {
            printUsage();
            return 1;
        }

        if (!Files.exists(input)) {
            System.err.println("Input not found: " + input);
            return 2;
        }

        try (FaceDetectionService service = new FaceDetectionService(model, confidence)) {
            service.setAnnotationOptions(new AnnotationOptions(showScore, showLandmarks));

            if (Files.isDirectory(input)) {
                Path outputDir = output != null ? output : input.resolve("output");
                System.out.printf("Scanning folder with %s (confidence: %.0f%%)...%n",
                        model.getDisplayName(), confidence * 100);

                BatchResult result = service.processFolder(input, outputDir, percent ->
                        System.out.printf("\rProcessing... %3d%%", percent));

                System.out.println();
                System.out.printf("Processed %d image(s), found %d face(s), %d failure(s) in %d ms.%n",
                        result.processed(), result.totalFaces(), result.failures(), result.elapsedMillis());
                System.out.println("Saved to: " + result.outputDir().toAbsolutePath());
                for (FileResult file : result.files()) {
                    if (!file.succeeded()) {
                        System.err.println("  FAILED " + file.input().getFileName()
                                + ": " + file.error());
                    }
                }
                return result.failures() == 0 ? 0 : 1;
            }

            Path outPath = output != null
                    ? output
                    : input.resolveSibling(ImageUtils.outputName(input));

            DetectionResult result = service.processFile(input, outPath);
            System.out.printf("%d face(s) detected in %d ms using %s%n",
                    result.faces().size(), result.elapsedMillis(), model.getDisplayName());
            System.out.println("Saved to: " + outPath.toAbsolutePath());
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("""
                Face Detection System (Upgraded with Deep Learning AI)

                GUI:
                  java -jar face-detector.jar

                Single image:
                  java -jar face-detector.jar <image> [output] [options]

                Folder (batch):
                  java -jar face-detector.jar <folder> [output-folder] [options]

                Options:
                  --model <yunet|haar>     Detection engine (default: yunet)
                  --confidence <0.1..0.9>  Confidence threshold for YuNet (default: 0.5)
                  --show-score             Draw confidence percentage badges on faces
                  --show-landmarks         Draw facial landmark points (eyes, nose, mouth)

                Supported images: jpg, jpeg, png, bmp, gif
                """);
    }
}
