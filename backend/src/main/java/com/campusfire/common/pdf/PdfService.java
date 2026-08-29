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
 * 中文字体优先从项目目录 fonts/ 加载（跨平台），其次按操作系统常见路径探测；
 * 校徽 Logo 从 classpath:brand/school-logo.png 读取，缺失时自动省略。
 */
@Component
public class PdfService {
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    public static final String BRAND_TITLE = "湖南科技职业学院";
    public static final String BRAND_SUBTITLE = "智慧消防巡检管理平台";
    private static final Color RED = new Color(180, 35, 24);
    private static final Color DARK = new Color(23, 33, 43);
    private static final Color GRAY = new Color(98, 114, 125);
    private static final Color BODY = new Color(69, 84, 94);
    private static final Color LIGHT_BG = new Color(250, 251, 252);
    private static final Color BORDER = new Color(226, 232, 236);

    private volatile BaseFont baseFont;
    private volatile byte[] logoBytes;

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

    private byte[] logo() {
        if (logoBytes != null) return logoBytes;
        synchronized (this) {
            if (logoBytes != null) return logoBytes;
            try (InputStream in = PdfService.class.getResourceAsStream("/brand/school-logo.png")) {
                if (in != null) logoBytes = readAll(in);
            } catch (Exception e) {
                log.warn("校徽 Logo 读取失败，PDF 左上角将不带校徽: {}", e.getMessage());
            }
            return logoBytes;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private Font font(float size, int style, Color color) {
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
        Document document = new Document(pageSize, 34, 34, 22, 20);
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
        addHeader(document);
        drawLine(document, writer, RED, 1.7f);

        document.add(introParagraph());

        document.add(sectionTitle("核心功能"));
        document.add(functionGrid());

        PdfPTable main = new PdfPTable(new float[]{4.2f, 1f});
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
        stepsContainer.addElement(stepsPanel(stepsData));
        main.addCell(stepsContainer);
        main.addCell(labelPanel(label));
        document.add(main);

        document.add(safetyBanner());
    }

    /** 页眉：左侧校徽与居中标题同行（左右等宽列保证标题绝对居中）。 */
    private void addHeader(Document document) throws Exception {
        PdfPTable header = new PdfPTable(new float[]{1.1f, 7.8f, 1.1f});
        header.setWidthPercentage(100);

        PdfPCell logoCell = noBorderCell();
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        byte[] bytes = logo();
        if (bytes != null) {
            Image logo = Image.getInstance(bytes);
            logo.scaleToFit(48f, 48f);
            logo.setAlignment(Element.ALIGN_LEFT | Element.ALIGN_MIDDLE);
            logoCell.addElement(logo);
        }
        header.addCell(logoCell);

        PdfPCell titleCell = noBorderCell();
        Paragraph school = new Paragraph(BRAND_TITLE, font(14.5f, Font.BOLD, DARK));
        school.setAlignment(Element.ALIGN_CENTER);
        school.setLeading(16f);
        titleCell.addElement(school);
        Paragraph platform = new Paragraph(BRAND_SUBTITLE, font(22f, Font.BOLD, RED));
        platform.setAlignment(Element.ALIGN_CENTER);
        platform.setLeading(24f);
        titleCell.addElement(platform);
        header.addCell(titleCell);

        header.addCell(noBorderCell());
        document.add(header);
    }

    /** 平台简介：首行空两格。 */
    private Paragraph introParagraph() {
        String text = "本平台由学校保卫部主导建设，软件学院 2024级软件技术3班 骆希同学设计开发，实现校园消防设施“一物一码”数字化管理："
                + "全校每台消防设施拥有专属二维码身份，巡检人员扫码即可完成定位校验、逐项检查、现场拍照，数据实时上传管理端，"
                + "形成“发现隐患—自动派单—整改闭环—数据看板”的完整安全治理链条。";
        Paragraph intro = new Paragraph(text, font(8.8f, Font.NORMAL, BODY));
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.setLeading(13.2f);
        intro.setFirstLineIndent(17.6f);
        intro.setSpacingBefore(9f);
        intro.setSpacingAfter(7f);
        intro.setIndentationLeft(14f);
        intro.setIndentationRight(14f);
        return intro;
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
            noCell.setMinimumHeight(45f);
            PdfPCell textCell = new PdfPCell();
            textCell.setBorder(Rectangle.NO_BORDER);
            textCell.setPaddingLeft(8f);
            textCell.setPaddingRight(7f);
            textCell.setPaddingTop(6f);
            textCell.setPaddingBottom(5f);
            textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            textCell.setMinimumHeight(45f);
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
            card.setMinimumHeight(45f);
            card.addElement(content);
            stack.addCell(card);
            if (index + 1 < stepsData.length) stack.addCell(spacerCell(5f));
        }
        return stack;
    }

    private PdfPCell labelPanel(LabelData label) throws Exception {
        PdfPCell panel = new PdfPCell();
        panel.setBorder(Rectangle.NO_BORDER);
        panel.setCellEvent(new RoundedPanel(LIGHT_BG, new Color(190, 202, 210), 5f, true));
        panel.setPadding(8f);
        panel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        panel.setMinimumHeight(204f);

        Image qr = Image.getInstance(qrImage(label.token, 400));
        qr.scaleAbsolute(102f, 102f);
        qr.setAlignment(Element.ALIGN_CENTER);
        panel.addElement(qr);

        Paragraph enter = new Paragraph("扫码进入小程序", font(9.6f, Font.BOLD, DARK));
        enter.setAlignment(Element.ALIGN_CENTER);
        enter.setLeading(11f);
        enter.setSpacingBefore(5f);
        panel.addElement(enter);

        Paragraph code = new Paragraph(ellipsize(label.displayCode, 26), font(8.8f, Font.BOLD, DARK));
        code.setAlignment(Element.ALIGN_CENTER);
        code.setLeading(10.5f);
        code.setSpacingBefore(6f);
        panel.addElement(code);

        Paragraph location = new Paragraph(ellipsize(compactLocation(label.location), 30), font(6.6f, Font.NORMAL, GRAY));
        location.setAlignment(Element.ALIGN_CENTER);
        location.setLeading(8.5f);
        location.setSpacingBefore(2.5f);
        panel.addElement(location);

        if (!isBlank(label.facility)) {
            Paragraph facility = new Paragraph(ellipsize(label.facility, 22), font(6.2f, Font.NORMAL, GRAY));
            facility.setAlignment(Element.ALIGN_CENTER);
            facility.setLeading(8f);
            facility.setSpacingBefore(1f);
            panel.addElement(facility);
        }
        return panel;
    }

    /** 底部安全宣传横幅：左侧红色标语块，右侧消防守则。 */
    private PdfPTable safetyBanner() throws Exception {
        PdfPTable banner = new PdfPTable(new float[]{0.30f, 0.70f});
        banner.setWidthPercentage(100);

        PdfPCell slogan = new PdfPCell();
        slogan.setBorder(Rectangle.NO_BORDER);
        slogan.setCellEvent(new RoundedPanel(RED, RED, 5f));
        slogan.setPaddingLeft(10f);
        slogan.setPaddingRight(10f);
        slogan.setVerticalAlignment(Element.ALIGN_MIDDLE);
        slogan.setMinimumHeight(62f);
        Paragraph sloganTitle = new Paragraph("消防安全 · 人人有责", font(12.5f, Font.BOLD, Color.WHITE));
        sloganTitle.setAlignment(Element.ALIGN_CENTER);
        sloganTitle.setLeading(15f);
        slogan.addElement(sloganTitle);
        Paragraph sloganSub = new Paragraph("全民消防 生命至上 预防为主 防消结合", font(6.6f, Font.NORMAL, new Color(255, 219, 214)));
        sloganSub.setAlignment(Element.ALIGN_CENTER);
        sloganSub.setLeading(9f);
        sloganSub.setSpacingBefore(3f);
        slogan.addElement(sloganSub);
        banner.addCell(slogan);

        PdfPCell rules = new PdfPCell();
        rules.setBorder(Rectangle.NO_BORDER);
        rules.setCellEvent(new RoundedPanel(LIGHT_BG, BORDER, 5f));
        rules.setPaddingLeft(14f);
        rules.setPaddingRight(8f);
        rules.setPaddingTop(5f);
        rules.setPaddingBottom(5f);
        rules.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rules.setMinimumHeight(62f);
        String[][] ruleLines = {
                {"爱护消防设施，严禁圈占、堵塞、遮挡、损毁"},
                {"灭火器、消防栓为应急救命设施，非紧急情况请勿动用"},
                {"发现设施缺损或二维码破损，请及时报告保卫部"},
                {"消防控制室及应急电话（24小时值班） ", "0731-82862855"},
                {"本平台由学校保卫部主导建设并统一管理"},
        };
        for (String[] line : ruleLines) {
            Paragraph p = new Paragraph();
            p.add(new Chunk("• ", font(8f, Font.BOLD, RED)));
            for (int i = 0; i < line.length; i++) {
                boolean highlight = i % 2 == 1;
                p.add(new Chunk(line[i], font(highlight ? 7.8f : 7.4f, Font.BOLD, highlight ? RED : BODY)));
            }
            p.setLeading(10.6f);
            rules.addElement(p);
        }
        banner.addCell(rules);
        return banner;
    }

    private void drawPosterFrame(PdfWriter writer, Rectangle pageSize) {
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setColorStroke(new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue()));
        cb.setLineWidth(0.7f);
        cb.roundRectangle(8f, 8f, pageSize.getWidth() - 16f, pageSize.getHeight() - 16f, 6f);
        cb.stroke();
    }

    private static class RoundedPanel implements PdfPCellEvent {
        private final Color fill;
        private final Color border;
        private final float radius;
        private final boolean dashed;

        private RoundedPanel(Color fill, Color border, float radius) {
            this(fill, border, radius, false);
        }

        private RoundedPanel(Color fill, Color border, float radius, boolean dashed) {
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
        p.setSpacingBefore(4f);
        p.setSpacingAfter(6f);
        return p;
    }

    private PdfPTable functionGrid() throws Exception {
        String[][] functions = {
                {"一物一码", "全校消防设施建档赋码，身份唯一、精准建档"},
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
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setCellEvent(new RoundedPanel(LIGHT_BG, BORDER, 4f));
            cell.setPaddingLeft(8f);
            cell.setPaddingRight(8f);
            cell.setPaddingTop(7f);
            cell.setPaddingBottom(6f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setMinimumHeight(48f);
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
                for (int col = 0; col < 5; col++) table.addCell(spacerCell(6f));
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

    private void drawLine(Document document, PdfWriter writer, Color color, float width) {
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
