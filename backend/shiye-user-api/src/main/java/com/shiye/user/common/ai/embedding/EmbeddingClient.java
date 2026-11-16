package com.shiye.user.common.ai.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    String model();

    int dimension();
}
