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
    void shouldKeepNineLabelsPerA4Page() throws Exception {
        List<PdfService.LabelData> labels = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            labels.add(new PdfService.LabelData("token-" + i, String.valueOf(i), "北院 / 1栋 / 1层",
                    "消火栓（FH-" + i + "）", "NO.1栋-1层-" + String.format("%02d", i)));
        }

        PdfReader reader = new PdfReader(pdfService.labelsPdf(labels));

        assertEquals(2, reader.getNumberOfPages());
        assertEquals(PageSize.A4.getWidth(), reader.getPageSize(1).getWidth(), 0.5f);
        assertEquals(PageSize.A4.getHeight(), reader.getPageSize(1).getHeight(), 0.5f);
        reader.close();
    }

    @Test
    void shouldKeepPlatformPosterOnOneA4Page() throws Exception {
        PdfReader reader = new PdfReader(pdfService.posterPdf(null));

        assertEquals(1, reader.getNumberOfPages());
        assertEquals(PageSize.A4.rotate().getWidth(), reader.getPageSizeWithRotation(1).getWidth(), 0.5f);
        assertEquals(PageSize.A4.rotate().getHeight(), reader.getPageSizeWithRotation(1).getHeight(), 0.5f);
        reader.close();
    }
}
