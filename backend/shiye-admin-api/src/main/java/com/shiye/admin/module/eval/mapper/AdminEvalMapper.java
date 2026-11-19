package com.shiye.admin.module.eval.mapper;

import com.shiye.admin.module.eval.domain.entity.EvalCaseEntity;
import com.shiye.admin.module.eval.domain.entity.EvalResultEntity;
import com.shiye.admin.module.eval.domain.entity.EvalRunEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminEvalMapper {

    @Insert("""
            INSERT INTO rag_eval_cases
                (user_id, question, expected_article_ids, expected_chunk_ids, gold_answer,
                 tags, status)
            VALUES (#{userId}, #{question}, CAST(#{expectedArticleIds} AS JSON),
                    CAST(#{expectedChunkIds} AS JSON), #{goldAnswer},
                    CAST(#{tags} AS JSON), #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCase(EvalCaseEntity entity);

    @Select("""
            <script>
            SELECT c.id, c.user_id, u.email AS user_email, c.question,
                   c.expected_article_ids, c.expected_chunk_ids, c.gold_answer, c.tags,
                   c.status, c.created_at, c.updated_at
            FROM rag_eval_cases c
            LEFT JOIN users u ON u.id = c.user_id
            WHERE 1 = 1
            <if test="userId != null"> AND c.user_id = #{userId}</if>
            <if test="status != null"> AND c.status = #{status}</if>
            ORDER BY c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<EvalCaseEntity> findCases(@Param("userId") Long userId,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM rag_eval_cases c
            WHERE 1 = 1
            <if test="userId != null"> AND c.user_id = #{userId}</if>
            <if test="status != null"> AND c.status = #{status}</if>
            </script>
            """)
    long countCases(@Param("userId") Long userId, @Param("status") String status);

    @Insert("""
            INSERT INTO rag_eval_runs (user_id, name, model, top_k, rerank)
            VALUES (#{userId}, #{name}, #{model}, #{topK}, #{rerank})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRun(EvalRunEntity entity);

    @Select("""
            SELECT r.id, r.user_id, u.display_name AS user_name, r.name, r.model,
                   r.embedding_model, r.top_k, r.rerank, r.metrics, r.started_at,
                   r.finished_at, r.created_at
            FROM rag_eval_runs r
            LEFT JOIN users u ON u.id = r.user_id
            WHERE r.id = #{id}
            """)
    EvalRunEntity findRun(@Param("id") long id);

    @Select("""
            <script>
            SELECT r.id, r.user_id, u.display_name AS user_name, r.name, r.model,
                   r.embedding_model, r.top_k, r.rerank, r.metrics, r.started_at,
                   r.finished_at, r.created_at
            FROM rag_eval_runs r
            LEFT JOIN users u ON u.id = r.user_id
            WHERE 1 = 1
            <if test="userId != null"> AND r.user_id = #{userId}</if>
            ORDER BY r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<EvalRunEntity> findRuns(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM rag_eval_runs r
            WHERE 1 = 1
            <if test="userId != null"> AND r.user_id = #{userId}</if>
            </script>
            """)
    long countRuns(@Param("userId") Long userId);

    @Select("""
            SELECT e.id, e.run_id, e.case_id, c.question, e.retrieved_chunk_ids,
                   e.recall_at_5, e.citation_accuracy, e.answer_correctness,
                   e.answer_text, e.judge_reason, e.latency_ms, e.cost, e.created_at
            FROM rag_eval_results e
            LEFT JOIN rag_eval_cases c ON c.id = e.case_id
            WHERE e.run_id = #{runId}
            ORDER BY e.id
            """)
    List<EvalResultEntity> findResults(@Param("runId") long runId);
}
