package com.shiye.user.module.article.support;

import java.util.List;

public record EnrichmentPayload(String summary, List<String> tags) {
}
