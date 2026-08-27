package com.smartqa.browser.multimodal;

import com.smartqa.ai.AiMediaPart;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Shrinks screenshots for Gemini. Full-page PNG (often 500KB+) causes timeouts and 503s.
 * Generic — no website knowledge.
 */
public final class EvidenceImageCompressor {

    private static final int MAX_WIDTH = 1280;
    private static final int COMPRESS_ABOVE_BYTES = 180_000;
    private static final float JPEG_QUALITY = 0.55f;

    private EvidenceImageCompressor() {
    }

    public static AiMediaPart compact(byte[] png) {
        if (png == null || png.length == 0) {
            return AiMediaPart.png(new byte[0]);
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) {
                return AiMediaPart.png(png);
            }
            boolean wide = source.getWidth() > MAX_WIDTH;
            if (!wide && png.length <= COMPRESS_ABOVE_BYTES) {
                return AiMediaPart.png(png);
            }
            BufferedImage scaled = scale(source);
            byte[] jpeg = toJpeg(scaled);
            if (jpeg.length > 0 && (jpeg.length < png.length || wide)) {
                return AiMediaPart.image(jpeg, "image/jpeg");
            }
        } catch (Exception ignored) {
            // Keep the original PNG if compression fails.
        }
        return AiMediaPart.png(png);
    }

    private static BufferedImage scale(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_WIDTH) {
            return source;
        }
        int nextHeight = Math.max(1, (int) Math.round(height * (MAX_WIDTH / (double) width)));
        BufferedImage scaled = new BufferedImage(MAX_WIDTH, nextHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, MAX_WIDTH, nextHeight, null);
        g.dispose();
        return scaled;
    }

    private static byte[] toJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return new byte[0];
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            BufferedImage rgb = image;
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
