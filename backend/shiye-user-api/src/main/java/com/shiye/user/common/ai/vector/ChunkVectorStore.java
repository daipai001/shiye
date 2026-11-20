package com.shiye.user.common.ai.vector;

public interface ChunkVectorStore {

    void upsert(long userId, long articleId, long chunkId, String model,
                String content, int charStart, int charEnd, float[] vector);

    void delete(long userId, long chunkId);

    void addToTeam(long teamId, long chunkId);

    void removeFromTeam(long teamId, long chunkId);
}
