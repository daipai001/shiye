package com.shiye.user.module.article.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlArticleParser {

    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z0-9]+");
    private static final List<String> NOISE_MARKERS = List.of(
            "cookie", "consent", "advert", "sidebar", "comment", "share",
            "related", "recommend", "newsletter", "subscribe", "breadcrumb",
            "pagination", "modal", "popup", "toolbar", "menu"
    );

    private final int maxTextChars;

    public HtmlArticleParser(@Value("${shiye.article.max-text-chars:500000}") int maxTextChars) {
        this.maxTextChars = Math.max(10000, maxTextChars);
    }

    public ParsedArticle parse(byte[] body, String baseUrl, String fallbackTitle) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(body), null, baseUrl);
        } catch (IOException | RuntimeException ex) {
            throw new IngestException("PARSE_FAILED", "HTML 解析失败", ex);
        }

        String metaTitle = firstMeta(document,
                "meta[property=og:title]", "meta[name=twitter:title]");
        String metaSummary = firstMeta(document,
                "meta[property=og:description]",
                "meta[name=description]",
                "meta[name=twitter:description]");
        String author = firstMeta(document,
                "meta[name=author]",
                "meta[property=article:author]");
        String heading = textOf(document.selectFirst("h1"));
        String documentTitle = document.title();

        document.select("script,style,noscript,iframe,svg,canvas,form,header,footer,nav,aside").remove();
        removeNoise(document);

        Element root = chooseContentRoot(document);
        String content = buildText(root);
        content = normalize(content);
        if (content.length() < 80) {
            String fallback = normalize(root == null ? "" : root.text());
            if (fallback.length() > content.length()) {
                content = fallback;
            }
        }
        if (content.length() < 80) {
            throw new IngestException("PARSE_EMPTY", "没有解析到有效正文");
        }
        if (content.length() > maxTextChars) {
            content = content.substring(0, maxTextChars);
        }

        String title = chooseTitle(metaTitle, heading, documentTitle, fallbackTitle, baseUrl);
        String summary = metaSummary == null ? "" : metaSummary.trim();
        if (summary.isEmpty()) {
            summary = content.substring(0, Math.min(220, content.length()));
            if (content.length() > 220) {
                summary += "…";
            }
        }
        if (summary.length() > 1000) {
            summary = summary.substring(0, 1000);
        }

        return new ParsedArticle(
                title,
                blankToNull(author),
                summary,
                content,
                detectLanguage(content),
                wordCount(content)
        );
    }

    private String firstMeta(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String value = element.hasAttr("content") ? element.attr("content") : element.text();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private void removeNoise(Document document) {
        for (Element element : document.select("div,section")) {
            String marker = (element.className() + " " + element.id()).toLowerCase(Locale.ROOT);
            for (String noise : NOISE_MARKERS) {
                if (marker.contains(noise)) {
                    element.remove();
                    break;
                }
            }
        }
    }

    private Element chooseContentRoot(Document document) {
        Element best = null;
        double bestScore = 0;
        for (Element candidate : document.select("article,main,[role=main],div,section")) {
            int textLength = candidate.text().length();
            if (textLength < 200) {
                continue;
            }
            int linkLength = candidate.select("a").text().length();
            int paragraphCount = candidate.select("p").size();
            double score = textLength - linkLength * 0.5 + paragraphCount * 30.0;
            String tag = candidate.tagName();
            if ("article".equals(tag)) {
                score += 180;
            } else if ("main".equals(tag)) {
                score += 100;
            } else if ("section".equals(tag)) {
                score += 20;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        return document.body() != null ? document.body() : document;
    }

    private String buildText(Element root) {
        if (root == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String lastBlock = "";
        Elements blocks = root.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption");
        for (Element block : blocks) {
            if (shouldSkip(block)) {
                continue;
            }
            String text = normalizeInline(block.text());
            if (text.isEmpty() || text.equals(lastBlock)) {
                continue;
            }
            lastBlock = text;
            String tag = block.tagName();
            if (tag.startsWith("h") && tag.length() == 2) {
                builder.append("\n\n")
                        .append("#".repeat(Math.max(1, tag.charAt(1) - '0')))
                        .append(' ')
                        .append(text);
            } else if ("li".equals(tag)) {
                builder.append("\n- ").append(text);
            } else {
                builder.append("\n\n").append(text);
            }
        }
        return builder.toString();
    }

    private boolean shouldSkip(Element element) {
        String tag = element.tagName();
        if (!"p".equals(tag)) {
            return false;
        }
        Element parent = element.parent();
        if (parent == null) {
            return false;
        }
        String parentTag = parent.tagName();
        return "li".equals(parentTag)
                || "blockquote".equals(parentTag)
                || "td".equals(parentTag)
                || "th".equals(parentTag);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("(?m) {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String normalizeInline(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String chooseTitle(String metaTitle, String heading, String documentTitle,
                               String fallbackTitle, String baseUrl) {
        if (fallbackTitle != null && !fallbackTitle.isBlank()
                && !fallbackTitle.equals(baseUrl) && fallbackTitle.length() >= 3) {
            return truncate(fallbackTitle.trim(), 512);
        }
        for (String candidate : new String[]{heading, metaTitle, documentTitle}) {
            if (candidate != null && !candidate.isBlank()) {
                return truncate(candidate.trim(), 512);
            }
        }
        return truncate(baseUrl, 512);
    }

    private String detectLanguage(String content) {
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            } else if (Character.isLetter(codePoint)) {
                latin++;
            }
            i += Character.charCount(codePoint);
        }
        if (cjk == 0 && latin == 0) {
            return null;
        }
        return cjk >= 20 && cjk >= latin * 0.15 ? "zh" : "en";
    }

    private int wordCount(String content) {
        int cjk = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            }
            i += Character.charCount(codePoint);
        }
        int latinWords = 0;
        Matcher matcher = LATIN_WORD.matcher(content);
        while (matcher.find()) {
            latinWords++;
        }
        long total = (long) cjk + latinWords;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }

    private String textOf(Element element) {
        return element == null ? null : element.text();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
