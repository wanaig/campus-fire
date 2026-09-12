package com.campusfire.inspection;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class PhotoWatermarkService {
    private static final String BUNDLED_FONT_PATH = "/fonts/simhei.ttf";
    private final String fontFamily = resolveFontFamily();

    public byte[] addWatermark(byte[] source, String imageFormat, List<String> lines) {
        try {
            BufferedImage input = ImageIO.read(new ByteArrayInputStream(source));
            if (input == null) throw new IllegalArgumentException("上传文件不是有效的现场照片");
            String format = "png".equalsIgnoreCase(imageFormat) ? "png" : "jpg";
            int imageType = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), imageType);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.drawImage(input, 0, 0, null);
                drawWatermark(graphics, output.getWidth(), output.getHeight(), lines);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream target = new ByteArrayOutputStream();
            if (!ImageIO.write(output, format, target)) throw new IllegalArgumentException("现场照片格式无法生成水印");
            return target.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("现场照片水印生成失败", e);
        }
    }

    private void drawWatermark(Graphics2D graphics, int width, int height, List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        int margin = Math.max(10, width / 40);
        int fontSize = Math.max(10, Math.min(26, width / 40));
        int maxByHeight = Math.max(10, height / 20);
        fontSize = Math.min(fontSize, maxByHeight);
        Font facilityFont = new Font(fontFamily, Font.BOLD, fontSize + 2);
        Font metaFont = new Font(fontFamily, Font.PLAIN, fontSize);
        FontMetrics facilityMetrics = graphics.getFontMetrics(facilityFont);
        FontMetrics metaMetrics = graphics.getFontMetrics(metaFont);
        int facilityLineHeight = facilityMetrics.getHeight() + Math.max(3, fontSize / 4);
        int metaLineHeight = metaMetrics.getHeight() + Math.max(2, fontSize / 5);
        int blockHeight = facilityLineHeight + metaLineHeight * Math.max(0, lines.size() - 1);
        int baseline = height - margin - blockHeight + facilityMetrics.getAscent();
        int textWidth = width - margin * 2;

        for (int index = 0; index < lines.size(); index++) {
            Font font = index == 0 ? facilityFont : metaFont;
            FontMetrics metrics = index == 0 ? facilityMetrics : metaMetrics;
            drawOutlinedText(graphics, font, fit(lines.get(index), metrics, textWidth), margin, baseline);
            baseline += index == 0 ? facilityLineHeight : metaLineHeight;
        }
    }

    private void drawOutlinedText(Graphics2D graphics, Font font, String text, int x, int baseline) {
        GlyphVector glyphs = font.createGlyphVector(graphics.getFontRenderContext(), text);
        Shape shape = glyphs.getOutline(x, baseline);
        float outline = Math.max(2f, font.getSize2D() / 8f);
        graphics.setStroke(new BasicStroke(outline, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(0, 0, 0, 205));
        graphics.draw(shape);
        graphics.setColor(Color.WHITE);
        graphics.fill(shape);
    }

    private String fit(String value, FontMetrics metrics, int maxWidth) {
        String text = value == null ? "" : value;
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maxWidth) end--;
        return text.substring(0, end) + suffix;
    }

    /**
     * 优先从 classpath 加载打包内置的黑体（服务器 Linux 环境无中文字体，避免水印乱码）；
     * 加载失败时回退到系统字体族（本地 Windows 开发环境可用）。
     */
    private String resolveFontFamily() {
        try (InputStream in = PhotoWatermarkService.class.getResourceAsStream(BUNDLED_FONT_PATH)) {
            if (in != null) {
                Font bundled = Font.createFont(Font.TRUETYPE_FONT, in);
                // 预创建常用字号并注册到 JVM 字体环境，new Font(...) 才能按族名找到它
                for (int size : new int[]{12, 14, 16, 18, 20, 22, 24, 26, 28, 30}) {
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(bundled.deriveFont((float) size));
                }
                if (bundled.canDisplayUpTo("校园消防巡检位置时间") == -1) return bundled.getFamily();
            }
        } catch (IOException | FontFormatException e) {
            // 字体内置加载失败，走系统字体回退
        }
        String sample = "校园消防巡检位置时间";
        String[] candidates = {"Microsoft YaHei", "Noto Sans CJK SC", "WenQuanYi Micro Hei", "SansSerif"};
        for (String candidate : candidates) {
            Font font = new Font(candidate, Font.PLAIN, 16);
            if (font.canDisplayUpTo(sample) == -1) return candidate;
        }
        return "SansSerif";
    }
}
