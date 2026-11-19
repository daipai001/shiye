package com.shiye.user.module.article.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件正文提取：PDF 用 PDFBox，Markdown/TXT 按 UTF-8 解码，失败时回退 GB18030。
 */
@Component
public class UploadTextExtractor {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt");
    private static final Charset GB18030 = Charset.forName("GB18030");

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
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new IngestException("PDF_PARSE_FAILED", "PDF 解析失败", ex);
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
