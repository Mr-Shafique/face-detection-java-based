package com.facedetector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class FaceDetectorTest {

    private BufferedImage loadLena() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/lena.jpg")) {
            assertNotNull(stream, "Test image lena.jpg is missing");
            return ImageIO.read(stream);
        }
    }

    @Test
    void detectsAtLeastOneFaceInSampleImageWithYuNet() throws Exception {
        BufferedImage image = loadLena();

        try (FaceDetector detector = new FaceDetector(DetectorType.YUNET, 0.5f)) {
            List<Face> faces = detector.detect(image);
            assertFalse(faces.isEmpty(), "Expected at least one face with YuNet");
            assertTrue(faces.get(0).confidence() > 0.5f, "Confidence should exceed 50%");
            assertFalse(faces.get(0).landmarks().isEmpty(), "YuNet should detect 5 facial landmarks");

            AnnotationOptions options = new AnnotationOptions(true, true);
            BufferedImage annotated = FaceAnnotator.annotate(image, faces, options);
            assertEquals(image.getWidth(), annotated.getWidth());
            assertEquals(image.getHeight(), annotated.getHeight());
        }
    }

    @Test
    void detectsWithHaarCascade() throws Exception {
        BufferedImage image = loadLena();

        try (FaceDetector detector = new FaceDetector(DetectorType.HAAR_CASCADE, 0.5f)) {
            List<Face> faces = detector.detect(image);
            assertFalse(faces.isEmpty(), "Expected at least one face with Haar cascade");

            BufferedImage annotated = FaceAnnotator.annotate(image, faces);
            assertEquals(image.getWidth(), annotated.getWidth());
            assertEquals(image.getHeight(), annotated.getHeight());
        }
    }

    @Test
    void detectsMultipleFacesInGroupImage() throws Exception {
        BufferedImage lena = loadLena();

        // Create a synthetic group of 3 people
        BufferedImage group = new BufferedImage(1500, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = group.createGraphics();
        g.drawImage(lena, 50, 50, 400, 400, null);
        g.drawImage(lena, 550, 80, 350, 350, null);
        g.drawImage(lena, 1000, 60, 420, 420, null);
        g.dispose();

        try (FaceDetector detector = new FaceDetector(DetectorType.YUNET, 0.5f)) {
            List<Face> faces = detector.detect(group);
            assertEquals(3, faces.size(), "Should detect all 3 faces in group");
        }
    }

    @Test
    void detectsTiltedAndAngledFaces() throws Exception {
        BufferedImage lena = loadLena();

        // Face tilted at 35 degrees
        BufferedImage canvas = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        AffineTransform at = new AffineTransform();
        at.translate(400, 400);
        at.rotate(Math.toRadians(35));
        at.translate(-256, -256);
        g.drawImage(lena, at, null);
        g.dispose();

        try (FaceDetector detector = new FaceDetector(DetectorType.YUNET, 0.4f)) {
            List<Face> faces = detector.detect(canvas);
            assertFalse(faces.isEmpty(), "YuNet should detect tilted / angled face");
        }
    }

    @Test
    void buildsAnnotatedOutputName() {
        assertEquals("photo_faces.png", ImageUtils.outputName(java.nio.file.Path.of("photo.png")));
        assertEquals("group_faces.jpg", ImageUtils.outputName(java.nio.file.Path.of("group.JPG")));
        assertEquals("scan_faces.png", ImageUtils.outputName(java.nio.file.Path.of("scan.bmp")));
        assertFalse(ImageUtils.isSupported(java.nio.file.Path.of("README")));
    }
}
