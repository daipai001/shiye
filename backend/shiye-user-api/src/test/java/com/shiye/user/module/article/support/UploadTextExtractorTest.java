package com.shiye.user.module.article.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadTextExtractorTest {

    private final UploadTextExtractor extractor = new UploadTextExtractor(
            false, "tesseract", "chi_sim+eng", 80, 20, 200, 60000);

    @Test
    void extractsUtf8Markdown() {
        String content = "# 拾页上传测试\n\nMarkdown 正文需要超过八十个字符才能通过正文校验。"
                + "这里继续补充一些内容，确保解析、切片和向量化流程可以正常执行。"
                + "自动摘要和标签会在资料进入 READY 后异步生成，不会阻塞上传接口。";
        ExtractedUpload upload = extractor.extract(
                "note.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));

        assertEquals(".md", upload.extension());
        assertEquals("text/markdown", upload.mimeType());
        assertTrue(upload.text().contains("拾页上传测试"));
    }

    @Test
    void extractsPdfText() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("ShiYe PDF upload test content for parsing, chunking and embedding. "
                        + "This sentence is intentionally long enough to pass the eighty character validation.");
                stream.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdf = output.toByteArray();
        }

        ExtractedUpload upload = extractor.extract("test.pdf", "application/pdf", pdf);

        assertEquals(".pdf", upload.extension());
        assertEquals("application/pdf", upload.mimeType());
        assertTrue(upload.text().contains("ShiYe PDF upload test"));
    }

    @Test
    void rejectsUnsupportedType() {
        IngestException ex = assertThrows(IngestException.class, () -> extractor.extract(
                "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a supported file".getBytes(StandardCharsets.UTF_8)));

        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
    }

    @Test
    void reportsMissingOcrCommandForScannedPdf() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 600, 200);
            graphics.setColor(Color.BLACK);
            graphics.drawString("scanned image only", 20, 100);
            graphics.dispose();
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(pdImage, 20, 500, 300, 100);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdf = output.toByteArray();
        }

        UploadTextExtractor ocrExtractor = new UploadTextExtractor(
                true, "__missing_tesseract__", "chi_sim+eng", 80, 20, 200, 5000);
        IngestException ex = assertThrows(IngestException.class,
                () -> ocrExtractor.extract("scan.pdf", "application/pdf", pdf));

        assertEquals("OCR_UNAVAILABLE", ex.getCode());
    }
}
