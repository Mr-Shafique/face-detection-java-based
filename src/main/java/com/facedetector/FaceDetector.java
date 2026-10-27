package com.facedetector;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;

/**
 * High-accuracy face detector backed by OpenCV YuNet deep learning model,
 * with optional fallback to legacy Haar cascade frontal detection.
 *
 * <p>YuNet accurately detects:
 * <ul>
 *   <li>Side poses and extreme profile angles</li>
 *   <li>Multiple faces in crowds or group photos</li>
 *   <li>Tilted, looking up/down, or partially occluded faces</li>
 *   <li>5 facial landmarks (eyes, nose, mouth corners) per face</li>
 * </ul>
 */
public final class FaceDetector implements AutoCloseable {

    public static final String YUNET_RESOURCE = "/face_detection_yunet_2023mar.onnx";
    public static final String CASCADE_RESOURCE = "/haarcascade_frontalface_default.xml";

    public static final float DEFAULT_SCORE_THRESHOLD = 0.50f;
    public static final float DEFAULT_NMS_THRESHOLD = 0.30f;
    public static final int DEFAULT_TOP_K = 5000;

    private final FaceDetectorYN yuNetDetector;
    private final CascadeClassifier haarClassifier;

    private DetectorType defaultType = DetectorType.YUNET;
    private float scoreThreshold = DEFAULT_SCORE_THRESHOLD;
    private float nmsThreshold = DEFAULT_NMS_THRESHOLD;

    public FaceDetector() {
        this(DetectorType.YUNET, DEFAULT_SCORE_THRESHOLD);
    }

    public FaceDetector(DetectorType type, float scoreThreshold) {
        this.defaultType = type;
        this.scoreThreshold = scoreThreshold;

        // Initialize YuNet deep learning detector
        Path yuNetModel = ImageUtils.extractResource(YUNET_RESOURCE);
        this.yuNetDetector = FaceDetectorYN.create(
                yuNetModel.toString(),
                "",
                new Size(320, 320),
                this.scoreThreshold,
                this.nmsThreshold,
                DEFAULT_TOP_K,
                0, // DNN_BACKEND_DEFAULT
                0  // DNN_TARGET_CPU
        );

        // Initialize Haar Cascade classifier
        Path cascadeFile = ImageUtils.extractResource(CASCADE_RESOURCE);
        CascadeClassifier cascade = new CascadeClassifier(cascadeFile.toString());
        if (cascade.empty()) {
            cascade.close();
            throw new IllegalStateException("Failed to load the face detection cascade.");
        }
        this.haarClassifier = cascade;
    }

    public synchronized void setDetectorType(DetectorType type) {
        this.defaultType = (type != null) ? type : DetectorType.YUNET;
    }

    public DetectorType getDetectorType() {
        return defaultType;
    }

    public synchronized void setScoreThreshold(float threshold) {
        this.scoreThreshold = Math.clamp(threshold, 0.05f, 0.99f);
        if (yuNetDetector != null) {
            yuNetDetector.setScoreThreshold(this.scoreThreshold);
        }
    }

    public float getScoreThreshold() {
        return scoreThreshold;
    }

    public synchronized void setNmsThreshold(float threshold) {
        this.nmsThreshold = Math.clamp(threshold, 0.05f, 0.99f);
        if (yuNetDetector != null) {
            yuNetDetector.setNMSThreshold(this.nmsThreshold);
        }
    }

    public float getNmsThreshold() {
        return nmsThreshold;
    }

    /**
     * Detects faces using the current detector type and confidence settings.
     */
    public synchronized List<Face> detect(BufferedImage image) {
        return detect(image, defaultType, scoreThreshold);
    }

    /**
     * Detects faces with explicit engine and confidence threshold.
     */
    public synchronized List<Face> detect(BufferedImage image, DetectorType type, float threshold) {
        if (type == DetectorType.HAAR_CASCADE) {
            return detectHaar(image);
        } else {
            return detectYuNet(image, threshold);
        }
    }

    private List<Face> detectYuNet(BufferedImage image, float threshold) {
        Mat color = ImageUtils.toMat(image);
        Mat faces = new Mat();
        try {
            yuNetDetector.setInputSize(new Size(color.cols(), color.rows()));
            yuNetDetector.setScoreThreshold(threshold);
            yuNetDetector.setNMSThreshold(nmsThreshold);

            yuNetDetector.detect(color, faces);

            int count = faces.rows();
            if (count <= 0) {
                return List.of();
            }

            List<Face> result = new ArrayList<>(count);
            FloatIndexer idx = faces.createIndexer();
            try {
                for (int i = 0; i < count; i++) {
                    float x = idx.get(i, 0);
                    float y = idx.get(i, 1);
                    float w = idx.get(i, 2);
                    float h = idx.get(i, 3);
                    float score = idx.get(i, 14);

                    // Landmarks: right eye, left eye, nose, right mouth, left mouth
                    List<FaceLandmark> landmarks = List.of(
                            new FaceLandmark(idx.get(i, 4), idx.get(i, 5), "Right Eye"),
                            new FaceLandmark(idx.get(i, 6), idx.get(i, 7), "Left Eye"),
                            new FaceLandmark(idx.get(i, 8), idx.get(i, 9), "Nose Tip"),
                            new FaceLandmark(idx.get(i, 10), idx.get(i, 11), "Right Mouth"),
                            new FaceLandmark(idx.get(i, 12), idx.get(i, 13), "Left Mouth")
                    );

                    int ix = Math.max(0, Math.round(x));
                    int iy = Math.max(0, Math.round(y));
                    int iw = Math.min(color.cols() - ix, Math.round(w));
                    int ih = Math.min(color.rows() - iy, Math.round(h));

                    if (iw > 0 && ih > 0) {
                        result.add(new Face(ix, iy, iw, ih, score, landmarks));
                    }
                }
            } finally {
                idx.release();
            }
            return List.copyOf(result);
        } finally {
            faces.close();
            color.close();
        }
    }

    private List<Face> detectHaar(BufferedImage image) {
        Mat color = ImageUtils.toMat(image);
        Mat gray = new Mat();
        RectVector rectangles = new RectVector();
        try {
            opencv_imgproc.cvtColor(color, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.equalizeHist(gray, gray);

            int minFaceSize = 30;
            haarClassifier.detectMultiScale(
                    gray,
                    rectangles,
                    1.1,
                    5,
                    0,
                    new Size(minFaceSize, minFaceSize),
                    new Size());

            List<Face> faces = new ArrayList<>((int) rectangles.size());
            for (long i = 0; i < rectangles.size(); i++) {
                Rect rect = rectangles.get(i);
                faces.add(new Face(rect.x(), rect.y(), rect.width(), rect.height(), 1.0f, List.of()));
            }
            return List.copyOf(faces);
        } finally {
            rectangles.close();
            gray.close();
            color.close();
        }
    }

    @Override
    public synchronized void close() {
        if (yuNetDetector != null) {
            yuNetDetector.close();
        }
        if (haarClassifier != null) {
            haarClassifier.close();
        }
    }
}
