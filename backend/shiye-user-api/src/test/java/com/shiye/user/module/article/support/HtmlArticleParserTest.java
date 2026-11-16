package com.shiye.user.module.article.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlArticleParserTest {

    private final HtmlArticleParser parser = new HtmlArticleParser(100000);

    @Test
    void extractsArticleAndRemovesNavigation() {
        String paragraph = "这是一段用于测试正文解析的中文内容，"
                + "它应该被完整保留下来，并参与后续的切片和向量化。".repeat(6);
        String html = """
                <html>
                  <head>
                    <title>站点标题</title>
                    <meta name="description" content="页面摘要" />
                  </head>
                  <body>
                    <nav>首页 导航 广告</nav>
                    <article>
                      <h1>正文标题</h1>
                      <p>%s</p>
                      <h2>小节</h2>
                      <p>%s</p>
                    </article>
                    <footer>版权信息</footer>
                  </body>
                </html>
                """.formatted(paragraph, paragraph);

        ParsedArticle parsed = parser.parse(
                html.getBytes(StandardCharsets.UTF_8),
                "https://example.com/article",
                "书签标题"
        );

        assertEquals("书签标题", parsed.title());
        assertEquals("页面摘要", parsed.summary());
        assertEquals("zh", parsed.language());
        assertTrue(parsed.contentText().contains("这是一段用于测试正文解析的中文内容"));
        assertTrue(parsed.contentText().contains("## 小节"));
        assertFalse(parsed.contentText().contains("首页 导航 广告"));
        assertTrue(parsed.wordCount() > 50);
    }

    @Test
    void throwsWhenNoReadableContent() {
        String html = "<html><body><nav>只有导航</nav></body></html>";
        boolean thrown = false;
        try {
            parser.parse(html.getBytes(StandardCharsets.UTF_8), "https://example.com", null);
        } catch (IngestException ex) {
            thrown = "PARSE_EMPTY".equals(ex.getCode());
        }
        assertTrue(thrown);
    }
}
