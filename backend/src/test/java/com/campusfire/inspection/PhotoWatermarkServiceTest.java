package com.campusfire.inspection;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoWatermarkServiceTest {
    @Test
    void shouldRenderVisibleWatermarkPanel() throws Exception {
        BufferedImage source = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(source, "jpg", original);

        byte[] watermarked = new PhotoWatermarkService().addWatermark(original.toByteArray(), "jpg", Arrays.asList(
                "设施：F000028 · 创新创业楼1层消防栓",
                "位置：天心校区北院 / 创新创业楼 / 1层",
                "巡检：保安01 · 时间：2026-09-01 12:00:00",
                "GPS：28.227780, 112.938860 · 精度约±65米"));

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(watermarked));
        assertTrue(watermarked.length > 0);
        boolean hasWatermarkPixel = false;
        for (int y = result.getHeight() - 150; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                if (result.getRGB(x, y) != Color.WHITE.getRGB()) {
                    hasWatermarkPixel = true;
                    break;
                }
            }
            if (hasWatermarkPixel) break;
        }
        assertTrue(hasWatermarkPixel);
        assertTrue((result.getRGB(3, result.getHeight() - 3) & 0xff) > 240);
    }
}
