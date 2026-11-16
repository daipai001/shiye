package com.shiye.user.common.ai.embedding;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 本地联调向量：由文本 hash 确定性地生成固定维度向量。
 *
 * <p>它不是语义向量，不能用于真实 RAG 效果评估；仅用于在未配置 BGE-M3 API 时跑通入库状态机。</p>
 */
@Component
@ConditionalOnProperty(name = "shiye.ai.embedding.provider", havingValue = "dev", matchIfMissing = true)
public class DevHashEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(DevHashEmbeddingClient.class);

    private final String model;
    private final int dimension;

    public DevHashEmbeddingClient(
            @Value("${shiye.ai.embedding.model:DEV-HASH-1024}") String model,
            @Value("${shiye.ai.embedding.dimension:1024}") int dimension) {
        this.model = model;
        this.dimension = Math.max(8, dimension);
    }

    @PostConstruct
    public void warn() {
        log.warn("Embedding provider=dev ({}), vectors are deterministic hashes, NOT BGE-M3. "
                + "Set shiye.ai.embedding.provider=http and EMBEDDING_API_KEY for real embeddings.", model);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            byte[] digest = digest(text);
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (digest[i] & 0xffL);
            }
            Random random = new Random(seed);
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) random.nextGaussian();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
