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
    private static final String DEFAULT_ENTRY_QR_TOKEN = "campus-fire://miniprogram/pages/login/login";

    private static final java.awt.Color RED = new java.awt.Color(180, 35, 24);
    private static final java.awt.Color DARK = new java.awt.Color(23, 33, 43);
    private static final java.awt.Color GRAY = new java.awt.Color(98, 114, 125);
    private static final java.awt.Color LIGHT_BG = new java.awt.Color(250, 251, 252);
    private static final java.awt.Color BORDER = new java.awt.Color(226, 232, 236);
    private static final java.awt.Color SOFT_RED = new java.awt.Color(253, 239, 237);
    private static final float LABEL_HEIGHT = 263f;
    private static final float LABEL_GAP = 12f;

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

    /** 二维码标签 PDF：A4 每页 3×3 = 9 张，标签之间保留裁切间距。 */
    public byte[] labelsPdf(java.util.List<LabelData> labels) throws Exception {
        Document document = new Document(PageSize.A4, 16, 16, 14, 14);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();
        int pageCount = (labels.size() + 8) / 9;
        for (int page = 0; page < pageCount; page++) {
            PdfPTable table = new PdfPTable(new float[]{1f, 0.067f, 1f, 0.067f, 1f});
            table.setWidthPercentage(100);
            table.setSplitRows(false);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    int index = page * 9 + row * 3 + column;
                    table.addCell(index < labels.size() ? labelCell(labels.get(index)) : emptyCell());
                    if (column < 2) table.addCell(spacerCell(LABEL_HEIGHT));
                }
                if (row < 2) {
                    for (int column = 0; column < 5; column++) table.addCell(spacerCell(LABEL_GAP));
                }
            }
            document.add(table);
            if (page + 1 < pageCount) document.newPage();
        }
        document.close();
        return out.toByteArray();
    }

    private PdfPCell labelCell(LabelData label) throws Exception {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setCellEvent(new RoundedDashedBorder());
        cell.setPaddingLeft(8f);
        cell.setPaddingRight(8f);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(12f);
        cell.setFixedHeight(LABEL_HEIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph school = new Paragraph(BRAND_TITLE, font(12.2f, Font.BOLD, DARK));
        school.setAlignment(Element.ALIGN_CENTER);
        school.setLeading(15f);
        cell.addElement(school);

        Chunk subtitleText = new Chunk(BRAND_SUBTITLE, font(7.3f, Font.NORMAL, GRAY));
        subtitleText.setCharacterSpacing(1.15f);
        Paragraph subtitle = new Paragraph(subtitleText);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setLeading(10f);
        subtitle.setSpacingBefore(1f);
        cell.addElement(subtitle);

        Image qr = Image.getInstance(qrImage(label.token, 360));
        qr.setAlignment(Element.ALIGN_CENTER);
        qr.scaleAbsolute(112f, 112f);
        qr.setSpacingBefore(8f);
        qr.setSpacingAfter(7f);
        cell.addElement(qr);

        Paragraph code = new Paragraph(label.displayCode, font(10.5f, Font.BOLD, DARK));
        code.setAlignment(Element.ALIGN_CENTER);
        code.setLeading(13f);
        cell.addElement(code);

        String locationText = isDamagedText(label.location) ? "位置信息待补充" : formatLabelLocation(label.location);
        Paragraph location = new Paragraph(ellipsize(locationText, 38), font(7.1f, Font.NORMAL, GRAY));
        location.setAlignment(Element.ALIGN_CENTER);
        location.setLeading(9f);
        location.setSpacingBefore(8f);
        location.setIndentationLeft(2f);
        location.setIndentationRight(2f);
        cell.addElement(location);
        return cell;
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(LABEL_HEIGHT);
        return cell;
    }

    private String formatLabelLocation(String location) {
        if (isBlank(location)) return BRAND_TITLE;
        String trimmed = location.trim();
        return trimmed.startsWith(BRAND_TITLE) ? trimmed : BRAND_TITLE + trimmed;
    }

    private static class RoundedDashedBorder implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.LINECANVAS];
            canvas.saveState();
            canvas.setColorStroke(new Color(174, 181, 186));
            canvas.setLineWidth(0.7f);
            canvas.setLineDash(2.8f, 2.2f, 0f);
            canvas.roundRectangle(position.getLeft() + 0.7f, position.getBottom() + 0.7f,
                    position.getWidth() - 1.4f, position.getHeight() - 1.4f, 7f);
            canvas.stroke();
            canvas.restoreState();
        }
    }

    /** 平台介绍页 PDF：A4 横向单页。 */
    public byte[] posterPdf(String entryQrToken) throws Exception {
        Rectangle pageSize = PageSize.A4.rotate();
        Document document = new Document(pageSize, 34, 34, 26, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();
        drawPosterFrame(writer, pageSize);

        Paragraph school = new Paragraph(BRAND_TITLE, font(15f, Font.BOLD, DARK));
        school.setAlignment(Element.ALIGN_CENTER);
        school.setLeading(17f);
        document.add(school);
        Paragraph platform = new Paragraph(BRAND_SUBTITLE, font(23f, Font.BOLD, RED));
        platform.setAlignment(Element.ALIGN_CENTER);
        platform.setLeading(25f);
        platform.setSpacingAfter(5f);
        document.add(platform);
        drawLine(document, writer, RED, 1.7f);

        String intro = "本平台由我校软件学院 2024级软件技术3班 学生自主设计与开发，实现校园消防设施「一物一码」数字化管理：全校每台消防设施拥有专属二维码身份，巡检人员扫码即可完成定位校验、逐项检查、现场拍照，数据实时上传管理端，形成发现隐患、自动派单、整改闭环、数据看板的完整安全治理链条。";
        Paragraph introPara = new Paragraph(intro, font(8.5f, Font.NORMAL, new java.awt.Color(69, 84, 94)));
        introPara.setLeading(13.5f);
        introPara.setSpacingBefore(10f);
        introPara.setSpacingAfter(7f);
        introPara.setIndentationLeft(16f);
        introPara.setIndentationRight(16f);
        document.add(introPara);

        document.add(sectionTitle("核心功能"));
        document.add(functionGrid());

        PdfPTable main = new PdfPTable(new float[]{4.25f, 1f});
        main.setWidthPercentage(100);
        main.setSpacingAfter(8f);
        PdfPCell stepsContainer = noBorderCell();
        stepsContainer.setPaddingRight(12f);
        stepsContainer.addElement(sectionTitle("巡检操作规范"));
        String[][] stepsData = {
                {"01", "扫码", "对准设施二维码扫描，系统自动识别设施身份与巡检任务"},
                {"02", "校验", "系统校验巡检人员定位，确认位于设施附近后方可检查"},
                {"03", "检查", "逐项核对检查内容，现场拍照留证，如实填写结果"},
                {"04", "提交", "数据实时上传管理端，异常项自动生成整改工单"},
        };
        for (String[] row : stepsData) {
            PdfPTable stepRow = new PdfPTable(new float[]{0.65f, 3.35f});
            stepRow.setWidthPercentage(100);
            stepRow.setSpacingAfter(4f);
            PdfPCell noCell = new PdfPCell(new Phrase(row[0], font(12f, Font.BOLD, RED)));
            noCell.setBorderColor(BORDER);
            noCell.setBackgroundColor(LIGHT_BG);
            noCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            noCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            noCell.setPadding(4f);
            noCell.setMinimumHeight(34f);
            PdfPCell textCell = new PdfPCell();
            textCell.setBorderColor(BORDER);
            textCell.setBackgroundColor(LIGHT_BG);
            textCell.setPadding(4f);
            textCell.addElement(new Phrase(row[1], font(8.5f, Font.BOLD, DARK)));
            textCell.addElement(new Phrase(row[2], font(7.2f, Font.NORMAL, GRAY)));
            stepRow.addCell(noCell);
            stepRow.addCell(textCell);
            stepsContainer.addElement(stepRow);
        }
        main.addCell(stepsContainer);

        String qrToken = isBlank(entryQrToken) ? DEFAULT_ENTRY_QR_TOKEN : entryQrToken;
        PdfPCell qrPanel = new PdfPCell();
        qrPanel.setBorderColor(new java.awt.Color(190, 202, 210));
        qrPanel.setBorderWidth(1f);
        qrPanel.setBackgroundColor(LIGHT_BG);
        qrPanel.setPadding(8f);
        qrPanel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrPanel.setMinimumHeight(168f);
        Image entryQr = Image.getInstance(qrImage(qrToken, 400));
        entryQr.scaleAbsolute(94f, 94f);
        entryQr.setAlignment(Element.ALIGN_CENTER);
        qrPanel.addElement(entryQr);
        Paragraph scanTitle = new Paragraph("扫码进入小程序", font(8.5f, Font.BOLD, DARK));
        scanTitle.setAlignment(Element.ALIGN_CENTER);
        scanTitle.setSpacingBefore(4f);
        qrPanel.addElement(scanTitle);
        Paragraph scanHint = new Paragraph("体验智慧巡检服务", font(6.8f, Font.NORMAL, GRAY));
        scanHint.setAlignment(Element.ALIGN_CENTER);
        qrPanel.addElement(scanHint);
        main.addCell(qrPanel);
        document.add(main);

        PdfPTable safety = new PdfPTable(new float[]{1, 2.8f});
        safety.setWidthPercentage(100);
        safety.setSpacingAfter(7f);
        PdfPCell slogan = new PdfPCell();
        slogan.setBackgroundColor(RED);
        slogan.setBorderColor(RED);
        slogan.setVerticalAlignment(Element.ALIGN_MIDDLE);
        slogan.setPadding(9f);
        Paragraph safetyTitle = new Paragraph("消防安全  ·  人人有责", font(14f, Font.BOLD, java.awt.Color.WHITE));
        safetyTitle.setAlignment(Element.ALIGN_CENTER);
        slogan.addElement(safetyTitle);
        Paragraph safetySub = new Paragraph("全民消防 生命至上   预防为主 防消结合", font(7f, Font.NORMAL, new java.awt.Color(255, 224, 220)));
        safetySub.setAlignment(Element.ALIGN_CENTER);
        slogan.addElement(safetySub);
        safety.addCell(slogan);
        PdfPCell rules = new PdfPCell();
        rules.setBorderColor(BORDER);
        rules.setBackgroundColor(LIGHT_BG);
        rules.setPadding(7f);
        String[] ruleLines = {
                "爱护消防设施，严禁圈占、埋压、遮挡、损毁",
                "灭火器、消防栓为应急救命设施，非紧急情况请勿动用",
                "发现设施缺损或二维码破损，请及时报告保卫处",
                "火警电话 119 · 校园保卫 24 小时值班",
        };
        for (String line : ruleLines) {
            rules.addElement(new Phrase("· " + line, font(7.5f, Font.NORMAL, new java.awt.Color(69, 84, 94))));
        }
        safety.addCell(rules);
        document.add(safety);

        drawLine(document, writer, RED, 1.2f);
        Paragraph footer = new Paragraph(BRAND_TITLE + "  ·  " + DEV_CREDIT, font(8f, Font.BOLD, DARK));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(24f);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private void drawPosterFrame(PdfWriter writer, Rectangle pageSize) {
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setColorStroke(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue()));
        cb.setLineWidth(0.7f);
        cb.roundRectangle(8f, 8f, pageSize.getWidth() - 16f, pageSize.getHeight() - 16f, 6f);
        cb.stroke();
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph();
        Chunk bar = new Chunk("  ", font(12f, Font.BOLD, RED));
        bar.setBackground(RED);
        p.add(bar);
        p.add(new Chunk("  " + text, font(12f, Font.BOLD, DARK)));
        p.setSpacingBefore(4f);
        p.setSpacingAfter(5f);
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
        table.setSpacingAfter(10f);
        for (int i = 0; i < functions.length; i++) {
            String[] f = functions[i];
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(BORDER);
            cell.setBackgroundColor(LIGHT_BG);
            cell.setPadding(6f);
            cell.setMinimumHeight(43f);
            cell.addElement(new Phrase(f[0], font(9.5f, Font.BOLD, DARK)));
            cell.addElement(new Phrase(f[1], font(7.3f, Font.NORMAL, GRAY)));
            table.addCell(cell);
            if (i % 3 != 2) table.addCell(spacerCell(0f));
            if (i == 2) {
                for (int col = 0; col < 5; col++) table.addCell(spacerCell(5f));
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

    private String padSerial(String serial) {
        if (serial == null) return "000";
        try {
            return String.format("%03d", Integer.parseInt(serial));
        } catch (NumberFormatException ignored) {
            return serial;
        }
    }

    private String ellipsize(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private boolean isDamagedText(String text) {
        return text != null && (text.indexOf('?') >= 0 || text.indexOf('\uFFFD') >= 0);
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
