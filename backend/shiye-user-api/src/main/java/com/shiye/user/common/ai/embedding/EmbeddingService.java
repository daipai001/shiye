package com.shiye.user.common.ai.embedding;

import com.shiye.user.common.ai.config.RuntimeModelConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一 embedding 入口：批量、重试、L2 归一化。
 */
@Service
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final RuntimeModelConfigService modelConfig;

    public EmbeddingService(EmbeddingClient embeddingClient,
                            RuntimeModelConfigService modelConfig) {
        this.embeddingClient = embeddingClient;
        this.modelConfig = modelConfig;
    }

    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int batchSize = modelConfig.embedding().batchSize();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            vectors.addAll(embedWithRetry(batch));
        }
        return vectors;
    }

    public String model() {
        return embeddingClient.model();
    }

    public int dimension() {
        return embeddingClient.dimension();
    }

    private List<float[]> embedWithRetry(List<String> batch) {
        EmbeddingException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<float[]> vectors = embeddingClient.embed(batch);
                if (vectors.size() != batch.size()) {
                    throw new EmbeddingException("EMBEDDING_BAD_RESPONSE", "Embedding 返回数量不匹配");
                }
                List<float[]> normalized = new ArrayList<>(vectors.size());
                for (float[] vector : vectors) {
                    normalized.add(normalize(vector));
                }
                return normalized;
            } catch (EmbeddingException ex) {
                last = ex;
                if (attempt < 2) {
                    sleep(300L * (attempt + 1));
                }
            }
        }
        throw last == null
                ? new EmbeddingException("EMBEDDING_FAILED", "Embedding 失败")
                : last;
    }

    private float[] normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            throw new EmbeddingException("EMBEDDING_BAD_VECTOR", "Embedding 向量为空");
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("EMBEDDING_INTERRUPTED", "Embedding 重试被中断", ex);
        }
    }
}
