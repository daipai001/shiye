package com.shiye.user.module.ingest.mapper;

import com.shiye.user.module.ingest.domain.entity.IngestTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IngestTaskMapper {

    @Insert("""
            INSERT INTO ingest_tasks
                (user_id, article_id, source_type, stage, status, idempotency_key, started_at)
            VALUES (#{userId}, #{articleId}, #{sourceType}, #{stage}, 'RUNNING',
                    #{idempotencyKey}, UTC_TIMESTAMP(3))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IngestTaskEntity task);

    @Update("""
            UPDATE ingest_tasks
            SET stage = #{stage}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int updateStage(@Param("id") long id, @Param("stage") String stage);

    @Update("""
            UPDATE ingest_tasks
            SET status = 'SUCCESS', stage = 'DONE', finished_at = UTC_TIMESTAMP(3),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int markSuccess(@Param("id") long id);

    @Update("""
            UPDATE ingest_tasks
            SET status = 'FAILED', error_code = #{errorCode}, error_message = #{errorMessage},
                finished_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int markFailed(@Param("id") long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);
}
