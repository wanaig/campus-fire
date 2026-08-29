package com.campusfire.common.pdf;

import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfServiceTest {
    private final PdfService pdfService = new PdfService();

    @Test
    void shouldCreateOneLandscapePagePerQrLabel() throws Exception {
        List<PdfService.LabelData> labels = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            labels.add(new PdfService.LabelData("token-" + i, String.valueOf(i), "北院 / 1栋 / 1层",
                    "消火栓（FH-" + i + "）", "NO.1栋-1层-" + String.format("%02d", i)));
        }

        byte[] pdf = pdfService.labelsPdf(labels);
        writeSample(Paths.get("target", "qr-labels-sample.pdf"), pdf);

        PdfReader reader = new PdfReader(pdf);

        assertEquals(3, reader.getNumberOfPages());
        assertEquals(PageSize.A4.rotate().getWidth(), reader.getPageSizeWithRotation(1).getWidth(), 0.5f);
        assertEquals(PageSize.A4.rotate().getHeight(), reader.getPageSizeWithRotation(1).getHeight(), 0.5f);
        reader.close();
        assertTrue(pdf.length > 0);
    }

    /** 样本仅供人工预览；文件被占用（如正在预览）时跳过写出，不影响测试结论。 */
    private void writeSample(Path sample, byte[] pdf) {
        try {
            Files.createDirectories(sample.getParent());
            Files.write(sample, pdf);
        } catch (IOException ignored) {
        }
    }
}
