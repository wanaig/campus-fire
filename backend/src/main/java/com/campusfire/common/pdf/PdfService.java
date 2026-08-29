package com.campusfire.common.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * PDF 生成组件：二维码标签与平台介绍页。
 * 中文字体优先从项目目录 fonts/ 加载（跨平台），其次按操作系统常见路径探测。
 */
@Component
public class PdfService {
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    public static final String BRAND_TITLE = "湖南科技职业学院";
    public static final String BRAND_SUBTITLE = "智慧消防巡检管理平台";
    public static final String DEV_CREDIT = "软件学院 2024级软件技术3班 开发团队";
    private static final java.awt.Color RED = new java.awt.Color(180, 35, 24);
    private static final java.awt.Color DARK = new java.awt.Color(23, 33, 43);
    private static final java.awt.Color GRAY = new java.awt.Color(98, 114, 125);
    private static final java.awt.Color LIGHT_BG = new java.awt.Color(250, 251, 252);
    private static final java.awt.Color BORDER = new java.awt.Color(226, 232, 236);

    private volatile BaseFont baseFont;

    private BaseFont font() {
        if (baseFont != null) return baseFont;
        synchronized (this) {
            if (baseFont != null) return baseFont;
            baseFont = loadFont();
            return baseFont;
        }
    }

    private BaseFont loadFont() {
        String[] candidates = {
                "fonts/chinese.ttf",
                "fonts/chinese.otf",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc,0",
                "C:/Windows/Fonts/simsun.ttc,0",
                "/usr/share/fonts/truetype/chinese/chinese.ttf",
                "/usr/share/fonts/chinese.ttf",
        };
        for (String path : candidates) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    try (InputStream in = new FileInputStream(file)) {
                        byte[] bytes = readAll(in);
                        return BaseFont.createFont("custom", "custom", BaseFont.EMBEDDED, BaseFont.CACHED, bytes, null);
                    }
                }
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) { }
        }
        try {
            return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("未找到可用的中文字体，请在 backend/fonts/chinese.ttf 放置中文字体文件");
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private Font font(float size, int style, java.awt.Color color) {
        return new Font(font(), size, style, color);
    }

    public byte[] qrImage(String token, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new MultiFormatWriter().encode(token, BarcodeFormat.QR_CODE, size, size, hints);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 二维码介绍标签 PDF：每个二维码使用一张横向 A4 页面。 */
    public byte[] labelsPdf(java.util.List<LabelData> labels) throws Exception {
        Rectangle pageSize = PageSize.A4.rotate();
        Document document = new Document(pageSize, 34, 34, 26, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) document.newPage();
            addLabelPage(document, writer, pageSize, labels.get(index));
        }
        document.close();
        return out.toByteArray();
    }

    private void addLabelPage(Document document, PdfWriter writer, Rectangle pageSize, LabelData label) throws Exception {
        drawPosterFrame(writer, pageSize);

        Paragraph school = new Paragraph(BRAND_TITLE, font(14f, Font.BOLD, DARK));
        school.setAlignment(Element.ALIGN_CENTER);
        school.setLeading(16f);
        document.add(school);
        Paragraph platform = new Paragraph(BRAND_SUBTITLE, font(21.5f, Font.BOLD, RED));
        platform.setAlignment(Element.ALIGN_CENTER);
        platform.setLeading(23.5f);
        platform.setSpacingAfter(6f);
        document.add(platform);
        drawLine(document, writer, RED, 1.7f);

        String intro = "本平台由我校软件学院 2024级软件技术3班 学生自主设计与开发，实现校园消防设施「一物一码」数字化管理：全校每台消防设施拥有专属二维码身份，巡检人员扫码即可完成定位校验、逐项检查、现场拍照，数据实时上传管理端，形成发现隐患、自动派单、整改闭环、数据看板的完整安全治理链条。";
        Paragraph introPara = new Paragraph(intro, font(8.7f, Font.NORMAL, new java.awt.Color(69, 84, 94)));
        introPara.setLeading(14f);
        introPara.setSpacingBefore(12f);
        introPara.setSpacingAfter(10f);
        introPara.setIndentationLeft(16f);
        introPara.setIndentationRight(16f);
        document.add(introPara);

        document.add(sectionTitle("核心功能"));
        document.add(functionGrid());

        PdfPTable main = new PdfPTable(new float[]{4.2f, 1f});
        main.setWidthPercentage(100);
        main.setSpacingAfter(14f);
        PdfPCell stepsContainer = noBorderCell();
        stepsContainer.setPaddingRight(12f);
        stepsContainer.addElement(sectionTitle("巡检操作规范"));
        String[][] stepsData = {
                {"01", "扫码", "对准设施二维码扫描，系统自动识别设施身份与巡检任务"},
                {"02", "校验", "系统校验巡检人员定位，确认位于设施附近后方可检查"},
                {"03", "检查", "逐项核对检查内容，现场拍照留证，如实填写结果"},
                {"04", "提交", "数据实时上传管理端，异常项自动生成整改工单"},
        };
        stepsContainer.addElement(stepsPanel(stepsData));
        main.addCell(stepsContainer);
        main.addCell(labelPanel(label));
        document.add(main);
        drawLine(document, writer, RED, 1.2f);
        Paragraph footer = new Paragraph(BRAND_TITLE + "  ·  " + DEV_CREDIT, font(8f, Font.BOLD, DARK));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(14f);
        document.add(footer);
    }

    private PdfPTable stepsPanel(String[][] stepsData) throws Exception {
        PdfPTable stack = new PdfPTable(1);
        stack.setWidthPercentage(100);
        for (int index = 0; index < stepsData.length; index++) {
            String[] row = stepsData[index];
            PdfPTable content = new PdfPTable(new float[]{0.45f, 3.55f});
            content.setWidthPercentage(100);
            PdfPCell noCell = new PdfPCell(new Phrase(row[0], font(11.5f, Font.BOLD, RED)));
            noCell.setBorder(Rectangle.NO_BORDER);
            noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            noCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            noCell.setPadding(5f);
            noCell.setMinimumHeight(48f);
            PdfPCell textCell = new PdfPCell();
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setPaddingLeft(8f);
            textCell.setPaddingRight(7f);
            textCell.setPaddingTop(7f);
            textCell.setPaddingBottom(6f);
            textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            textCell.setMinimumHeight(48f);
            Paragraph stepTitle = new Paragraph(row[1], font(9f, Font.BOLD, DARK));
            stepTitle.setLeading(10f);
            stepTitle.setSpacingAfter(1.5f);
            textCell.addElement(stepTitle);
            Paragraph stepDescription = new Paragraph(row[2], font(7.6f, Font.NORMAL, GRAY));
            stepDescription.setLeading(9.5f);
            textCell.addElement(stepDescription);
            content.addCell(noCell);
            content.addCell(textCell);

            PdfPCell card = new PdfPCell();
            card.setBorder(Rectangle.NO_BORDER);
            card.setCellEvent(new RoundedPanel(LIGHT_BG, BORDER, 4f));
            card.setPadding(0f);
            card.setMinimumHeight(48f);
            card.addElement(content);
            stack.addCell(card);
            if (index + 1 < stepsData.length) stack.addCell(spacerCell(6f));
        }
        return stack;
    }

    private PdfPCell labelPanel(LabelData label) throws Exception {
        PdfPCell panel = new PdfPCell();
        panel.setBorder(Rectangle.NO_BORDER);
        panel.setCellEvent(new RoundedPanel(LIGHT_BG, new java.awt.Color(190, 202, 210), 5f, true));
        panel.setPadding(9f);
        panel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        panel.setMinimumHeight(229f);

        Image qr = Image.getInstance(qrImage(label.token, 400));
        qr.scaleAbsolute(106f, 106f);
        qr.setAlignment(Element.ALIGN_CENTER);
        panel.addElement(qr);

        Paragraph code = new Paragraph(ellipsize(label.displayCode, 28), font(9.4f, Font.BOLD, DARK));
        code.setAlignment(Element.ALIGN_CENTER);
        code.setLeading(11f);
        code.setSpacingBefore(5f);
        panel.addElement(code);

        Paragraph location = new Paragraph(ellipsize(compactLocation(label.location), 30), font(7f, Font.NORMAL, GRAY));
        location.setAlignment(Element.ALIGN_CENTER);
        location.setLeading(9f);
        location.setSpacingBefore(3f);
        panel.addElement(location);

        if (!isBlank(label.facility)) {
            Paragraph facility = new Paragraph(ellipsize(label.facility, 22), font(6.6f, Font.NORMAL, GRAY));
            facility.setAlignment(Element.ALIGN_CENTER);
            facility.setLeading(8f);
            facility.setSpacingBefore(1f);
            panel.addElement(facility);
        }

        Paragraph action = new Paragraph("请使用小程序扫码巡检", font(7.4f, Font.BOLD, DARK));
        action.setAlignment(Element.ALIGN_CENTER);
        action.setLeading(9f);
        action.setSpacingBefore(5f);
        panel.addElement(action);
        return panel;
    }

    private void drawPosterFrame(PdfWriter writer, Rectangle pageSize) {
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setColorStroke(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue()));
        cb.setLineWidth(0.7f);
        cb.roundRectangle(8f, 8f, pageSize.getWidth() - 16f, pageSize.getHeight() - 16f, 6f);
        cb.stroke();
    }

    private static class RoundedPanel implements PdfPCellEvent {
        private final java.awt.Color fill;
        private final java.awt.Color border;
        private final float radius;
        private final boolean dashed;

        private RoundedPanel(java.awt.Color fill, java.awt.Color border, float radius) {
            this(fill, border, radius, false);
        }

        private RoundedPanel(java.awt.Color fill, java.awt.Color border, float radius, boolean dashed) {
            this.fill = fill;
            this.border = border;
            this.radius = radius;
            this.dashed = dashed;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            canvas.saveState();
            canvas.setColorFill(new Color(fill.getRed(), fill.getGreen(), fill.getBlue()));
            canvas.setColorStroke(new Color(border.getRed(), border.getGreen(), border.getBlue()));
            canvas.setLineWidth(0.65f);
            if (dashed) canvas.setLineDash(2.6f, 2.2f, 0f);
            canvas.roundRectangle(position.getLeft() + 0.5f, position.getBottom() + 0.5f,
                    position.getWidth() - 1f, position.getHeight() - 1f, radius);
            canvas.fillStroke();
            canvas.restoreState();
        }
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph();
        Chunk bar = new Chunk(" ", font(11.3f, Font.BOLD, RED));
        bar.setBackground(RED);
        p.add(bar);
        p.add(new Chunk("  " + text, font(11.3f, Font.BOLD, DARK)));
        p.setSpacingBefore(6f);
        p.setSpacingAfter(7f);
        return p;
    }

    private PdfPTable functionGrid() throws Exception {
        String[][] functions = {
                {"一物一码", "全校消防设施建档赋码，身份唯一、档案集中"},
                {"扫码巡检", "扫描设施二维码即可开展当次巡检，任务自动匹配"},
                {"定位校验", "系统自动核验巡检人员是否位于设施现场，杜绝远程代检"},
                {"拍照留痕", "仅限现场拍摄取证，拍摄时间与位置双重校验"},
                {"隐患闭环", "检查异常自动生成整改单，跟踪至整改完成销号"},
                {"数据看板", "巡检完成率、逾期、整改态势实时汇总呈现"},
        };
        PdfPTable table = new PdfPTable(new float[]{1f, 0.035f, 1f, 0.035f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(13f);
        for (int i = 0; i < functions.length; i++) {
            String[] f = functions[i];
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setCellEvent(new RoundedPanel(LIGHT_BG, BORDER, 4f));
            cell.setPaddingLeft(8f);
            cell.setPaddingRight(8f);
            cell.setPaddingTop(8f);
            cell.setPaddingBottom(7f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setMinimumHeight(50f);
            Paragraph functionTitle = new Paragraph(f[0], font(9.7f, Font.BOLD, DARK));
            functionTitle.setLeading(11f);
            functionTitle.setSpacingAfter(2f);
            cell.addElement(functionTitle);
            Paragraph functionDescription = new Paragraph(f[1], font(7.6f, Font.NORMAL, GRAY));
            functionDescription.setLeading(9.5f);
            cell.addElement(functionDescription);
            table.addCell(cell);
            if (i % 3 != 2) table.addCell(spacerCell(0f));
            if (i == 2) {
                for (int col = 0; col < 5; col++) table.addCell(spacerCell(7f));
            }
        }
        return table;
    }

    private PdfPCell spacerCell(float height) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(height);
        return cell;
    }

    private void drawLine(Document document, PdfWriter writer, java.awt.Color color, float width) {
        PdfContentByte cb = writer.getDirectContent();
        cb.setColorStroke(new Color(color.getRed(), color.getGreen(), color.getBlue()));
        cb.setLineWidth(width);
        cb.moveTo(document.left(), writer.getVerticalPosition(true) - 2);
        cb.lineTo(document.right(), writer.getVerticalPosition(true) - 2);
        cb.stroke();
    }

    private PdfPCell noBorderCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        return cell;
    }

    private String ellipsize(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private String compactLocation(String location) {
        if (isBlank(location)) return "位置信息待补充";
        String compact = location.trim();
        String brandPrefix = BRAND_TITLE + " / ";
        if (compact.startsWith(brandPrefix)) compact = compact.substring(brandPrefix.length());
        return compact;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static class LabelData {
        public final String token;
        public final String serial;
        public final String location;
        public final String facility;
        public final String displayCode;

        public LabelData(String token, String serial, String location, String facility) {
            this(token, serial, location, facility, "NO." + padSerialValue(serial));
        }

        public LabelData(String token, String serial, String location, String facility, String displayCode) {
            this.token = token;
            this.serial = serial;
            this.location = location;
            this.facility = facility;
            this.displayCode = displayCode;
        }

        private static String padSerialValue(String serial) {
            if (serial == null) return "000";
            try {
                return String.format("%03d", Integer.parseInt(serial));
            } catch (NumberFormatException ignored) {
                return serial;
            }
        }
    }
}
