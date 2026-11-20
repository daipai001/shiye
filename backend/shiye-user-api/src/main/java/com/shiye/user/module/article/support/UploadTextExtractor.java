package com.shiye.user.module.article.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 上传文件正文提取：PDF 用 PDFBox，Markdown/TXT 按 UTF-8 解码，失败时回退 GB18030。
 */
@Component
public class UploadTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(UploadTextExtractor.class);
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt");
    private static final Charset GB18030 = Charset.forName("GB18030");

    private final boolean ocrEnabled;
    private final String ocrCommand;
    private final String ocrLang;
    private final int ocrMinTextChars;
    private final int ocrMaxPages;
    private final int ocrDpi;
    private final long ocrTimeoutMs;

    public UploadTextExtractor(
            @Value("${shiye.article.ocr-enabled:false}") boolean ocrEnabled,
            @Value("${shiye.article.ocr-command:tesseract}") String ocrCommand,
            @Value("${shiye.article.ocr-lang:chi_sim+eng}") String ocrLang,
            @Value("${shiye.article.ocr-min-text-chars:80}") int ocrMinTextChars,
            @Value("${shiye.article.ocr-max-pages:20}") int ocrMaxPages,
            @Value("${shiye.article.ocr-dpi:200}") int ocrDpi,
            @Value("${shiye.article.ocr-timeout-ms:60000}") long ocrTimeoutMs) {
        this.ocrEnabled = ocrEnabled;
        this.ocrCommand = ocrCommand == null || ocrCommand.isBlank()
                ? "tesseract" : ocrCommand.trim();
        this.ocrLang = ocrLang == null || ocrLang.isBlank() ? "chi_sim+eng" : ocrLang.trim();
        this.ocrMinTextChars = Math.max(1, ocrMinTextChars);
        this.ocrMaxPages = Math.max(1, Math.min(ocrMaxPages, 100));
        this.ocrDpi = Math.max(72, Math.min(ocrDpi, 400));
        this.ocrTimeoutMs = Math.max(5000, ocrTimeoutMs);
    }

    public ExtractedUpload extract(String originalName, String suppliedMime, byte[] bytes) {
        String fileName = safeFileName(originalName);
        String extension = extensionOf(fileName, suppliedMime);
        String text;
        if ("pdf".equals(extension)) {
            text = extractPdf(bytes);
        } else if (TEXT_EXTENSIONS.contains(extension)) {
            text = decodeText(bytes);
        } else {
            throw new IngestException("UNSUPPORTED_FILE_TYPE", "仅支持 PDF / Markdown / TXT");
        }
        text = normalize(text);
        if (text.length() < 80) {
            throw new IngestException("PARSE_EMPTY", "没有提取到有效正文");
        }
        return new ExtractedUpload(text, "." + extension, mimeOf(extension));
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (!ocrEnabled || text.strip().length() >= ocrMinTextChars) {
                return text;
            }
            return ocrPdf(document);
        } catch (IngestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IngestException("PDF_PARSE_FAILED", "PDF 解析失败", ex);
        }
    }

    private String ocrPdf(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder text = new StringBuilder();
        int pages = Math.min(document.getNumberOfPages(), ocrMaxPages);
        for (int page = 0; page < pages; page++) {
            BufferedImage image = renderer.renderImageWithDPI(page, ocrDpi, ImageType.RGB);
            Path temp = Files.createTempFile("shiye-ocr-", ".png");
            try {
                if (!ImageIO.write(image, "png", temp.toFile())) {
                    throw new IngestException("OCR_FAILED", "OCR 页面渲染失败");
                }
                String pageText = runTesseract(temp);
                if (!pageText.isBlank()) {
                    text.append(pageText).append("\n\n");
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return text.toString();
    }

    private String runTesseract(Path image) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    ocrCommand, image.toString(), "stdout",
                    "-l", ocrLang
            ).start();
            if (!process.waitFor(ocrTimeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IngestException("OCR_FAILED", "OCR 处理超时");
            }
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (process.exitValue() != 0) {
                String error = new String(process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                log.warn("tesseract exited with {}: {}", process.exitValue(), error);
                throw new IngestException("OCR_FAILED", "OCR 处理失败");
            }
            return output.trim();
        } catch (IngestException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IngestException("OCR_FAILED", "OCR 处理被中断", ex);
        } catch (IOException ex) {
            throw new IngestException("OCR_UNAVAILABLE",
                    "扫描版 PDF 需要 OCR，但未找到 tesseract 命令", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return stripBom(text);
        } catch (CharacterCodingException ex) {
            return stripBom(new String(bytes, GB18030));
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "";
        }
        try {
            return Paths.get(originalName).getFileName().toString();
        } catch (InvalidPathException ex) {
            return originalName.replaceAll("[\\\\/]", "_");
        }
    }

    private String extensionOf(String fileName, String mime) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if ("markdown".equals(extension)) {
                return "md";
            }
            return extension;
        }
        if (mime != null) {
            String value = mime.toLowerCase(Locale.ROOT);
            if (value.contains("pdf")) {
                return "pdf";
            }
            if (value.contains("markdown")) {
                return "md";
            }
            if (value.startsWith("text/")) {
                return "txt";
            }
        }
        return "";
    }

    private String mimeOf(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "md" -> "text/markdown";
            default -> "text/plain";
        };
    }
}
