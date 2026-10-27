package com.facedetector;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Conversions between Swing/AWT images and OpenCV matrices, plus image file IO.
 */
public final class ImageUtils {

    public static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "bmp", "gif");

    private static final Map<String, Path> EXTRACTED_RESOURCES = new ConcurrentHashMap<>();

    private ImageUtils() {
    }

    public static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported or unreadable image: " + path);
        }
        return image;
    }

    public static void write(BufferedImage image, Path path) throws IOException {
        String format = extension(path);
        if (format.isEmpty()) {
            format = "png";
        }
        BufferedImage output = image;
        if (format.equals("jpg") || format.equals("jpeg")) {
            output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String writerFormat = format.equals("jpeg") ? "jpg" : format;
        if (!ImageIO.write(output, writerFormat, path.toFile())) {
            throw new IOException("No image writer available for format: " + format);
        }
    }

    /**
     * Converts a BufferedImage to a CV_8UC3 BGR matrix.
     */
    public static Mat toMat(BufferedImage source) {
        BufferedImage bgr = source;
        if (source.getType() != BufferedImage.TYPE_3BYTE_BGR || source.getRaster().getParent() != null) {
            bgr = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D graphics = bgr.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        byte[] pixels = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), opencv_core.CV_8UC3);
        mat.data().put(pixels);
        return mat;
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    public static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static boolean isSupported(Path path) {
        return SUPPORTED_EXTENSIONS.contains(extension(path));
    }

    /**
     * Name of the annotated copy, keeping the original extension when possible.
     */
    public static String outputName(Path input) {
        String extension = extension(input);
        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
            extension = "png";
        }
        return baseName(input) + "_faces." + extension;
    }

    /**
     * Extracts a classpath resource to a temporary file, preserving the original extension.
     */
    public static Path extractResource(String resource) {
        return EXTRACTED_RESOURCES.computeIfAbsent(resource, res -> {
            try (var stream = ImageUtils.class.getResourceAsStream(res)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing classpath resource: " + res);
                }
                String ext = ".tmp";
                int dot = res.lastIndexOf('.');
                if (dot >= 0) {
                    ext = res.substring(dot);
                }
                Path temp = Files.createTempFile("face-detector-", ext);
                temp.toFile().deleteOnExit();
                Files.copy(stream, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return temp;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not extract resource " + res, e);
            }
        });
    }
}
