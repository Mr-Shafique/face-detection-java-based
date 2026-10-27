package com.facedetector;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws annotations around detected faces (red circles, optional confidence badges and landmarks).
 */
public final class FaceAnnotator {

    private FaceAnnotator() {
    }

    public static BufferedImage annotate(BufferedImage source, List<Face> faces) {
        return annotate(source, faces, AnnotationOptions.DEFAULT);
    }

    public static BufferedImage annotate(
            BufferedImage source, List<Face> faces, AnnotationOptions options) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(
                    RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.drawImage(source, 0, 0, null);

            int thickness = Math.max(2, Math.round(result.getWidth() / 400f));
            graphics.setColor(Color.RED);
            graphics.setStroke(new BasicStroke(
                    thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Font for confidence badges
            int fontSize = Math.max(12, Math.min(32, Math.round(result.getWidth() / 80f)));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics();

            for (Face face : faces) {
                int radius = face.radius();
                int cx = face.centerX();
                int cy = face.centerY();

                // Draw red circle
                graphics.setColor(Color.RED);
                graphics.setStroke(new BasicStroke(
                        thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

                // Optional: Draw landmarks
                if (options != null && options.showLandmarks() && !face.landmarks().isEmpty()) {
                    int dotRadius = Math.max(2, Math.round(thickness * 1.2f));
                    for (FaceLandmark lm : face.landmarks()) {
                        Color dotColor = switch (lm.name()) {
                            case "Right Eye", "Left Eye" -> Color.CYAN;
                            case "Nose Tip" -> Color.YELLOW;
                            default -> Color.GREEN;
                        };
                        graphics.setColor(dotColor);
                        int lx = Math.round(lm.x());
                        int ly = Math.round(lm.y());
                        graphics.fillOval(lx - dotRadius, ly - dotRadius, dotRadius * 2, dotRadius * 2);
                        graphics.setColor(Color.BLACK);
                        graphics.setStroke(new BasicStroke(1));
                        graphics.drawOval(lx - dotRadius, ly - dotRadius, dotRadius * 2, dotRadius * 2);
                    }
                }

                // Optional: Draw confidence badge
                if (options != null && options.showConfidence()) {
                    String text = Math.round(face.confidence() * 100) + "%";
                    int textWidth = metrics.stringWidth(text);
                    int textHeight = metrics.getAscent();

                    int badgeX = cx - (textWidth / 2);
                    int badgeY = cy - radius - 6;
                    if (badgeY - textHeight < 4) {
                        badgeY = cy + radius + textHeight + 6;
                    }

                    int pad = Math.max(2, fontSize / 4);
                    graphics.setColor(new Color(20, 20, 20, 190));
                    graphics.fillRoundRect(
                            badgeX - pad,
                            badgeY - textHeight - 2,
                            textWidth + pad * 2,
                            textHeight + pad,
                            pad * 2,
                            pad * 2);

                    graphics.setColor(Color.WHITE);
                    graphics.drawString(text, badgeX, badgeY - 2);
                }
            }
            return result;
        } finally {
            graphics.dispose();
        }
    }
}
