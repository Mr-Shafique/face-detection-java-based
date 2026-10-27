package com.facedetector.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

/**
 * Displays an image scaled to fit while preserving its aspect ratio.
 */
public final class ImagePanel extends JComponent {

    private static final Color BACKGROUND = new Color(28, 28, 32);

    private BufferedImage image;

    public ImagePanel() {
        setPreferredSize(new Dimension(900, 620));
        setMinimumSize(new Dimension(320, 240));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());

            if (image == null) {
                g.setColor(new Color(160, 160, 170));
                String message = "No image loaded";
                int width = g.getFontMetrics().stringWidth(message);
                g.drawString(message, (getWidth() - width) / 2, getHeight() / 2);
                return;
            }

            double scale = Math.min(
                    (getWidth() - 24.0) / image.getWidth(),
                    (getHeight() - 24.0) / image.getHeight());
            scale = Math.max(scale, 0.01);

            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g.drawImage(image, x, y, width, height, null);
        } finally {
            g.dispose();
        }
    }
}
