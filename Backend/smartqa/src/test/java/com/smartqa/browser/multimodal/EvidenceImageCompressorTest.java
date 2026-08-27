package com.smartqa.browser.multimodal;

import com.smartqa.ai.AiMediaPart;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceImageCompressorTest {

    @Test
    void shrinksLargePngForMultimodalAi() throws Exception {
        BufferedImage image = new BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < 1600; y += 8) {
            for (int x = 0; x < 2400; x += 8) {
                g.setColor(new Color((x * 13 + y * 7) & 0xFF, (x * 3) & 0xFF, (y * 11) & 0xFF));
                g.fillRect(x, y, 8, 8);
            }
        }
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        byte[] original = png.toByteArray();

        AiMediaPart compacted = EvidenceImageCompressor.compact(original);
        assertTrue(compacted.sizeBytes() > 0);
        assertTrue(compacted.mimeType().contains("jpeg"));
        assertTrue(compacted.sizeBytes() < original.length || original.length < 50_000);
    }
}
