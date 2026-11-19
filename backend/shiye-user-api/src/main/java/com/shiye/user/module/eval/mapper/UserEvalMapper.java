package com.shiye.user.module.eval.mapper;

import com.shiye.user.module.eval.domain.entity.EvalCaseRow;
import com.shiye.user.module.eval.domain.entity.EvalRunRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UserEvalMapper {

    @Select("""
            SELECT id, user_id, name, model, top_k, rerank, started_at, finished_at
            FROM rag_eval_runs
            WHERE started_at IS NULL
            ORDER BY id
            LIMIT 1
            """)
    EvalRunRow findPendingRun();

    @Update("""
            UPDATE rag_eval_runs
            SET started_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND started_at IS NULL
            """)
    int markStarted(@Param("id") long id);

    @Select("""
            SELECT id, user_id, question, expected_article_ids, expected_chunk_ids, gold_answer
            FROM rag_eval_cases
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            ORDER BY id
            """)
    List<EvalCaseRow> findActiveCases(@Param("userId") long userId);

    @Insert("""
            INSERT INTO rag_eval_results
                (run_id, case_id, retrieved_chunk_ids, recall_at_5, citation_accuracy,
                 answer_correctness, answer_text, judge_reason, latency_ms, cost)
            VALUES (#{runId}, #{caseId}, CAST(#{retrievedChunkIds} AS JSON),
                    #{recallAt5}, #{citationAccuracy}, #{answerCorrectness},
                    #{answerText}, #{judgeReason}, #{latencyMs}, #{cost})
            """)
    int insertResult(@Param("runId") long runId,
                     @Param("caseId") long caseId,
                     @Param("retrievedChunkIds") String retrievedChunkIds,
                     @Param("recallAt5") BigDecimal recallAt5,
                     @Param("citationAccuracy") BigDecimal citationAccuracy,
                     @Param("answerCorrectness") BigDecimal answerCorrectness,
                     @Param("answerText") String answerText,
                     @Param("judgeReason") String judgeReason,
                     @Param("latencyMs") int latencyMs,
                     @Param("cost") BigDecimal cost);

    @Update("""
            UPDATE rag_eval_runs
            SET metrics = CAST(#{metrics} AS JSON),
                embedding_model = #{embeddingModel},
                model = #{model},
                top_k = #{topK},
                finished_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int finishRun(@Param("id") long id,
                  @Param("metrics") String metrics,
                  @Param("embeddingModel") String embeddingModel,
                  @Param("model") String model,
                  @Param("topK") int topK);
}
