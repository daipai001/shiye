package com.shiye.user.common.ai.vector;

public interface ChunkVectorStore {

    void upsert(long userId, long articleId, long chunkId, String model,
                String content, int charStart, int charEnd, float[] vector);

    void delete(long userId, long chunkId);
}
