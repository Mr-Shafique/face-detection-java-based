package com.facedetector.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.facedetector.AnnotationOptions;
import com.facedetector.BatchResult;
import com.facedetector.DetectionResult;
import com.facedetector.DetectorType;
import com.facedetector.FaceAnnotator;
import com.facedetector.FaceDetectionService;
import com.facedetector.FileResult;
import com.facedetector.ImageUtils;

/**
 * Main Swing window: load one image or a whole folder, detect faces with AI or Haar cascade,
 * adjust confidence, view landmarks, and save results.
 */
public final class MainWindow extends JFrame {

    private final FaceDetectionService service = new FaceDetectionService();

    private final ImagePanel preview = new ImagePanel();
    private final JLabel status = new JLabel("Open an image or a folder to begin.");
    private final JProgressBar progress = new JProgressBar(0, 100);

    private final JButton openImageButton = new JButton("Open Image");
    private final JButton openFolderButton = new JButton("Open Folder (Batch)");
    private final JButton detectButton = new JButton("Detect Faces");
    private final JButton saveButton = new JButton("Save Result");

    private final JComboBox<DetectorType> modelCombo = new JComboBox<>(DetectorType.values());
    private final JSlider confidenceSlider = new JSlider(10, 95, 50);
    private final JLabel confidenceLabel = new JLabel("Confidence: 50%");
    private final JCheckBox showConfidenceCheck = new JCheckBox("Show % Score", false);
    private final JCheckBox showLandmarksCheck = new JCheckBox("Show Landmarks", false);

    private BufferedImage currentImage;
    private File currentFile;
    private DetectionResult currentResult;
    private boolean busy;

    public MainWindow() {
        super("Face Detection System (Upgraded with Deep Learning AI)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(840, 600));
        setSize(1080, 760);
        setLocationRelativeTo(null);

        // Actions row
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        actionsRow.add(openImageButton);
        actionsRow.add(openFolderButton);
        actionsRow.add(new JSeparator(SwingConstants.VERTICAL));
        actionsRow.add(detectButton);
        actionsRow.add(saveButton);
        detectButton.setEnabled(false);
        saveButton.setEnabled(false);

        // Settings row
        JPanel settingsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        settingsRow.add(new JLabel("Engine:"));
        settingsRow.add(modelCombo);
        settingsRow.add(Box.createHorizontalStrut(8));
        settingsRow.add(confidenceLabel);
        confidenceSlider.setPreferredSize(new Dimension(130, 22));
        settingsRow.add(confidenceSlider);
        settingsRow.add(Box.createHorizontalStrut(8));
        settingsRow.add(showConfidenceCheck);
        settingsRow.add(showLandmarksCheck);

        // Combined top header
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 0, 0));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, java.awt.Color.LIGHT_GRAY));
        topPanel.add(actionsRow);
        topPanel.add(settingsRow);

        // Bottom status row
        JPanel bottom = new JPanel(new BorderLayout(10, 4));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        bottom.add(status, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(220, 16));
        progress.setStringPainted(true);
        progress.setVisible(false);
        bottom.add(progress, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(preview, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Event listeners
        openImageButton.addActionListener(this::chooseImage);
        openFolderButton.addActionListener(this::chooseFolder);
        detectButton.addActionListener(event -> detectCurrent());
        saveButton.addActionListener(this::saveResult);

        modelCombo.addActionListener(e -> {
            DetectorType selected = (DetectorType) modelCombo.getSelectedItem();
            service.setDetectorType(selected);
            boolean isYuNet = (selected == DetectorType.YUNET);
            confidenceSlider.setEnabled(isYuNet);
            confidenceLabel.setEnabled(isYuNet);
            showConfidenceCheck.setEnabled(isYuNet);
            showLandmarksCheck.setEnabled(isYuNet);
        });

        confidenceSlider.addChangeListener(e -> {
            int val = confidenceSlider.getValue();
            confidenceLabel.setText("Confidence: " + val + "%");
            service.setScoreThreshold(val / 100.0f);
        });

        showConfidenceCheck.addActionListener(e -> updateAnnotationOptions());
        showLandmarksCheck.addActionListener(e -> updateAnnotationOptions());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                service.close();
            }
        });
    }

    private void updateAnnotationOptions() {
        AnnotationOptions opts = new AnnotationOptions(
                showConfidenceCheck.isSelected(),
                showLandmarksCheck.isSelected());
        service.setAnnotationOptions(opts);

        if (currentResult != null && currentImage != null) {
            BufferedImage annotated = FaceAnnotator.annotate(
                    currentImage, currentResult.faces(), opts);
            currentResult = new DetectionResult(
                    currentResult.faces(),
                    annotated,
                    currentResult.elapsedMillis(),
                    currentResult.detectorType());
            preview.setImage(annotated);
        }
    }

    private void chooseImage(ActionEvent event) {
        if (busy) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open an image");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (jpg, jpeg, png, bmp, gif)", "jpg", "jpeg", "png", "bmp", "gif"));
        setStartDirectory(chooser);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new IOException("Unsupported or corrupt image file.");
            }
            currentImage = image;
            currentFile = file;
            currentResult = null;
            preview.setImage(image);
            detectButton.setEnabled(true);
            saveButton.setEnabled(false);
            status.setText("Loaded " + file.getName() + " ("
                    + image.getWidth() + " x " + image.getHeight() + " px)");
        } catch (IOException ex) {
            showError("Could not open image", ex);
        }
    }

    private void detectCurrent() {
        if (busy || currentImage == null) {
            return;
        }
        BufferedImage image = currentImage;
        setBusy(true);
        DetectorType type = (DetectorType) modelCombo.getSelectedItem();
        status.setText("Detecting faces with " + type.getDisplayName() + "...");

        new SwingWorker<DetectionResult, Void>() {
            @Override
            protected DetectionResult doInBackground() {
                return service.process(image);
            }

            @Override
            protected void done() {
                try {
                    DetectionResult result = get();
                    currentResult = result;
                    preview.setImage(result.annotatedImage());
                    saveButton.setEnabled(true);

                    if (result.faces().isEmpty()) {
                        status.setText("No faces detected. Tip: lower the confidence slider if faces are angled or in shadow.");
                    } else {
                        status.setText(result.faces().size() + " face(s) detected in "
                                + result.elapsedMillis() + " ms [" + result.detectorType().getDisplayName() + "]");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("Detection interrupted.");
                } catch (ExecutionException ex) {
                    status.setText("Detection failed.");
                    showError("Detection failed",
                            ex.getCause() == null ? ex : ex.getCause());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void saveResult(ActionEvent event) {
        if (busy || currentResult == null || currentFile == null) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save annotated image");
        chooser.setCurrentDirectory(currentFile.getParentFile());
        chooser.setSelectedFile(new File(
                currentFile.getParentFile(),
                ImageUtils.baseName(currentFile.toPath()) + "_faces.png"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path output = chooser.getSelectedFile().toPath();
        if (!output.getFileName().toString().contains(".")) {
            output = output.resolveSibling(output.getFileName() + ".png");
        }

        try {
            ImageUtils.write(currentResult.annotatedImage(), output);
            status.setText("Saved: " + output.getFileName());
        } catch (IOException ex) {
            showError("Could not save image", ex);
        }
    }

    private void chooseFolder(ActionEvent event) {
        if (busy) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder of images");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path imagesDir = Path.of("images");
        if (Files.isDirectory(imagesDir)) {
            chooser.setCurrentDirectory(imagesDir.toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path folder = chooser.getSelectedFile().toPath();
        Path outputDir = folder.resolve("output");
        setBusy(true);
        progress.setValue(0);
        progress.setVisible(true);
        status.setText("Processing batch in " + folder.getFileName() + "...");

        SwingWorker<BatchResult, Void> worker = new SwingWorker<>() {
            @Override
            protected BatchResult doInBackground() throws Exception {
                return service.processFolder(folder, outputDir, percent -> setProgress(percent));
            }

            @Override
            protected void done() {
                progress.setVisible(false);
                try {
                    BatchResult result = get();
                    if (result.processed() == 0) {
                        status.setText("No supported images found in " + folder.getFileName() + ".");
                        JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "No jpg, jpeg, png, bmp or gif files were found in:\n" + folder,
                                "Batch finished",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    preview.setImage(null);
                    currentImage = null;
                    currentResult = null;
                    currentFile = null;
                    detectButton.setEnabled(false);
                    saveButton.setEnabled(false);
                    status.setText(result.processed() + " image(s) processed, "
                            + result.totalFaces() + " face(s) found in "
                            + result.elapsedMillis() + " ms");

                    StringBuilder message = new StringBuilder()
                            .append("Images processed: ").append(result.processed())
                            .append("\nFaces found: ").append(result.totalFaces())
                            .append("\nFailures: ").append(result.failures())
                            .append("\nEngine: ").append(service.getDetectorType().getDisplayName())
                            .append("\n\nSaved annotated copies to:\n")
                            .append(result.outputDir().toAbsolutePath());
                    if (result.failures() > 0) {
                        message.append("\n\nDetails:");
                        int shown = 0;
                        for (FileResult file : result.files()) {
                            if (!file.succeeded() && shown < 8) {
                                message.append("\n- ").append(file.input().getFileName())
                                        .append(": ").append(file.error());
                                shown++;
                            }
                        }
                    }
                    JOptionPane.showMessageDialog(
                            MainWindow.this, message.toString(),
                            "Batch finished", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("Batch interrupted.");
                } catch (ExecutionException ex) {
                    status.setText("Batch failed.");
                    showError("Batch failed",
                            ex.getCause() == null ? ex : ex.getCause());
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.addPropertyChangeListener(change -> {
            if ("progress".equals(change.getPropertyName())) {
                progress.setValue((Integer) change.getNewValue());
            }
        });
        worker.execute();
    }

    private void setStartDirectory(JFileChooser chooser) {
        if (currentFile != null && currentFile.getParentFile() != null) {
            chooser.setCurrentDirectory(currentFile.getParentFile());
            return;
        }
        Path imagesDir = Path.of("images");
        if (Files.isDirectory(imagesDir)) {
            chooser.setCurrentDirectory(imagesDir.toFile());
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        openImageButton.setEnabled(!busy);
        openFolderButton.setEnabled(!busy);
        detectButton.setEnabled(!busy && currentImage != null);
        saveButton.setEnabled(!busy && currentResult != null);
        modelCombo.setEnabled(!busy);
        confidenceSlider.setEnabled(!busy && modelCombo.getSelectedItem() == DetectorType.YUNET);
        showConfidenceCheck.setEnabled(!busy && modelCombo.getSelectedItem() == DetectorType.YUNET);
        showLandmarksCheck.setEnabled(!busy && modelCombo.getSelectedItem() == DetectorType.YUNET);
        setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void showError(String title, Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.toString();
        }
        JOptionPane.showMessageDialog(
                this, title + ":\n\n" + message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
