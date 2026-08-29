package com.campusfire.common.pdf;

import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfServiceTest {
    private final PdfService pdfService = new PdfService();

    @Test
    void shouldCreateOneLandscapePagePerQrLabel() throws Exception {
        List<PdfService.LabelData> labels = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            labels.add(new PdfService.LabelData("token-" + i, String.valueOf(i), "北院 / 1栋 / 1层",
                    "消火栓（FH-" + i + "）", "NO.1栋-1层-" + String.format("%02d", i)));
        }

        PdfReader reader = new PdfReader(pdfService.labelsPdf(labels));

        assertEquals(3, reader.getNumberOfPages());
        assertEquals(PageSize.A4.rotate().getWidth(), reader.getPageSizeWithRotation(1).getWidth(), 0.5f);
        assertEquals(PageSize.A4.rotate().getHeight(), reader.getPageSizeWithRotation(1).getHeight(), 0.5f);
        reader.close();
    }
}
