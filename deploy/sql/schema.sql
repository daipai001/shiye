-- ============================================================
-- 拾页 ShiYe：V1 数据库基线
-- MySQL 8.0+
-- 用法：
--   CREATE DATABASE IF NOT EXISTS shiye
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--   USE shiye;
--   SOURCE deploy/sql/schema.sql;
-- 约定：
--   - InnoDB / utf8mb4 / utf8mb4_0900_ai_ci
--   - 不使用物理外键；归属关系由应用层校验
--   - 时间统一 DATETIME(3)，UTC
--   - 金额 DECIMAL，token BIGINT/INT
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 迁移记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `version`      VARCHAR(32)  NOT NULL COMMENT '迁移版本，如 V001',
  `description`  VARCHAR(255) NOT NULL COMMENT '说明',
  `checksum`     CHAR(64)     NOT NULL COMMENT '文件 SHA-256',
  `applied_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '执行时间',
  `execution_ms` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='迁移记录';

-- ------------------------------------------------------------
-- 1. 身份与访问
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`                VARCHAR(190)    NOT NULL COMMENT '登录邮箱',
  `password_hash`        VARCHAR(100)    NOT NULL COMMENT 'BCrypt',
  `display_name`         VARCHAR(64)     NULL DEFAULT NULL COMMENT '显示名',
  `avatar_url`           VARCHAR(512)    NULL DEFAULT NULL COMMENT '头像',
  `status`               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  `monthly_token_quota`  BIGINT UNSIGNED NOT NULL DEFAULT 200000 COMMENT '月 token 配额',
  `monthly_cost_quota`   DECIMAL(12,6)   NOT NULL DEFAULT 5.000000 COMMENT '月成本配额',
  `quota_source`         VARCHAR(16)     NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT / NEW_USER / ADMIN',
  `email_verified_at`    DATETIME(3)     NULL DEFAULT NULL COMMENT 'P1',
  `last_login_at`        DATETIME(3)     NULL DEFAULT NULL,
  `created_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at`           DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `idx_users_status_created` (`status`, `created_at`),
  KEY `idx_users_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通用户';

CREATE TABLE IF NOT EXISTS `admin_users` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`         VARCHAR(190)    NOT NULL COMMENT '登录邮箱',
  `password_hash` VARCHAR(100)    NOT NULL COMMENT 'BCrypt',
  `name`          VARCHAR(64)     NOT NULL COMMENT '显示名',
  `role`          VARCHAR(32)     NOT NULL DEFAULT 'SUPER_ADMIN' COMMENT 'SUPER_ADMIN / OPERATOR / VIEWER',
  `status`        VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  `last_login_at` DATETIME(3)     NULL DEFAULT NULL,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_users_email` (`email`),
  KEY `idx_admin_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台管理员';

CREATE TABLE IF NOT EXISTS `teams` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`                  VARCHAR(64)     NOT NULL,
  `owner_user_id`         BIGINT UNSIGNED NOT NULL,
  `status`                VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  `monthly_token_quota`   BIGINT UNSIGNED NOT NULL DEFAULT 1000000,
  `monthly_cost_quota`    DECIMAL(12,6)   NOT NULL DEFAULT 20.000000,
  `created_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at`            DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_teams_owner` (`owner_user_id`),
  KEY `idx_teams_status` (`status`),
  KEY `idx_teams_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队';

CREATE TABLE IF NOT EXISTS `team_members` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`    BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `role`       VARCHAR(16)     NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER / ADMIN / MEMBER',
  `status`     VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  `joined_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_members_team_user` (`team_id`, `user_id`),
  KEY `idx_team_members_user_status` (`user_id`, `status`),
  KEY `idx_team_members_team_status` (`team_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队成员';

CREATE TABLE IF NOT EXISTS `team_invites` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`          BIGINT UNSIGNED NOT NULL,
  `inviter_user_id`  BIGINT UNSIGNED NOT NULL,
  `token_hash`       CHAR(64)        NOT NULL,
  `role`             VARCHAR(16)     NOT NULL DEFAULT 'MEMBER',
  `expires_at`       DATETIME(3)     NOT NULL,
  `used_at`          DATETIME(3)     NULL DEFAULT NULL,
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_invites_token` (`token_hash`),
  KEY `idx_team_invites_team_expires` (`team_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队邀请码';

CREATE TABLE IF NOT EXISTS `team_tags` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`         BIGINT UNSIGNED NOT NULL,
  `name`            VARCHAR(64)     NOT NULL,
  `normalized_name` VARCHAR(64)     NOT NULL,
  `color`           VARCHAR(16)     NULL DEFAULT NULL,
  `created_by`      BIGINT UNSIGNED NOT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_tags_name` (`team_id`, `normalized_name`),
  KEY `idx_team_tags_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队标签';

CREATE TABLE IF NOT EXISTS `article_team_tags` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `article_id` BIGINT UNSIGNED NOT NULL,
  `team_id`    BIGINT UNSIGNED NOT NULL,
  `tag_id`     BIGINT UNSIGNED NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_team_tags` (`article_id`, `tag_id`),
  KEY `idx_article_team_tags_tag` (`tag_id`, `team_id`),
  KEY `idx_article_team_tags_team_article` (`team_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料-团队标签';

CREATE TABLE IF NOT EXISTS `team_folders` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`    BIGINT UNSIGNED NOT NULL,
  `name`       VARCHAR(64)     NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_folders_name` (`team_id`, `name`),
  KEY `idx_team_folders_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队文件夹';

CREATE TABLE IF NOT EXISTS `article_team_folders` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `article_id` BIGINT UNSIGNED NOT NULL,
  `team_id`    BIGINT UNSIGNED NOT NULL,
  `folder_id`  BIGINT UNSIGNED NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_team_folders` (`article_id`, `folder_id`),
  KEY `idx_article_team_folders_folder` (`folder_id`, `team_id`),
  KEY `idx_article_team_folders_team_article` (`team_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料-团队文件夹';

CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `subject_type`   VARCHAR(16)     NOT NULL COMMENT 'USER / ADMIN',
  `subject_id`     BIGINT UNSIGNED NOT NULL,
  `token_hash`     CHAR(64)        NOT NULL COMMENT 'SHA-256(refresh token)',
  `expires_at`     DATETIME(3)     NOT NULL,
  `revoked_at`     DATETIME(3)     NULL DEFAULT NULL,
  `replaced_by_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `user_agent`     VARCHAR(512)    NULL DEFAULT NULL,
  `ip`             VARCHAR(45)     NULL DEFAULT NULL,
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_hash` (`token_hash`),
  KEY `idx_refresh_tokens_subject` (`subject_type`, `subject_id`, `expires_at`),
  KEY `idx_refresh_tokens_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷新令牌';

CREATE TABLE IF NOT EXISTS `extension_auth_codes` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `code_hash`  CHAR(64)        NOT NULL COMMENT 'SHA-256(code)',
  `expires_at` DATETIME(3)     NOT NULL,
  `used_at`    DATETIME(3)     NULL DEFAULT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_extension_codes_hash` (`code_hash`),
  KEY `idx_extension_codes_user` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件一次性授权码';

CREATE TABLE IF NOT EXISTS `user_plugin_settings` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `ball_enabled`   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '悬浮球开关',
  `default_mode`   VARCHAR(16)     NOT NULL DEFAULT 'LIBRARY' COMMENT 'LIBRARY / CURRENT / GENERAL',
  `default_scope`  VARCHAR(16)     NOT NULL DEFAULT 'ALL' COMMENT 'ALL / FOLDER / TAG',
  `default_scope_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'FOLDER/TAG 默认范围 ID',
  `shortcut`       VARCHAR(64)     NULL DEFAULT NULL,
  `site_blacklist` JSON            NULL DEFAULT NULL,
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_settings_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户插件/悬浮球偏好';

-- ------------------------------------------------------------
-- 2. 书签与文件
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bookmark_folders` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `browser`        VARCHAR(16)     NOT NULL COMMENT 'CHROME / EDGE',
  `folder_id`      VARCHAR(128)    NOT NULL COMMENT '浏览器 folderId',
  `folder_path`    VARCHAR(512)    NOT NULL,
  `is_primary`     TINYINT(1)      NOT NULL DEFAULT 0,
  `auto_sync`      TINYINT(1)      NOT NULL DEFAULT 1,
  `last_synced_at` DATETIME(3)     NULL DEFAULT NULL,
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bookmark_folders_browser` (`user_id`, `browser`, `folder_id`),
  KEY `idx_bookmark_folders_primary` (`user_id`, `is_primary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户选择的书签文件夹';

CREATE TABLE IF NOT EXISTS `bookmark_imports` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`            BIGINT UNSIGNED NOT NULL,
  `bookmark_folder_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `browser`            VARCHAR(16)     NOT NULL,
  `folder_id`          VARCHAR(128)    NOT NULL,
  `folder_path`        VARCHAR(512)    NOT NULL,
  `recursive`          TINYINT(1)      NOT NULL DEFAULT 1,
  `status`             VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / PAUSED / DONE / FAILED / CANCELLED',
  `total`              INT UNSIGNED    NOT NULL DEFAULT 0,
  `success`            INT UNSIGNED    NOT NULL DEFAULT 0,
  `failed`             INT UNSIGNED    NOT NULL DEFAULT 0,
  `skipped`            INT UNSIGNED    NOT NULL DEFAULT 0,
  `error_code`         VARCHAR(64)     NULL DEFAULT NULL,
  `started_at`         DATETIME(3)     NULL DEFAULT NULL,
  `finished_at`        DATETIME(3)     NULL DEFAULT NULL,
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_bookmark_imports_user` (`user_id`, `created_at`),
  KEY `idx_bookmark_imports_status` (`status`, `created_at`),
  KEY `idx_bookmark_imports_folder` (`bookmark_folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='书签导入任务';

CREATE TABLE IF NOT EXISTS `bookmark_items` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `import_id`         BIGINT UNSIGNED NOT NULL,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `title`             VARCHAR(512)    NULL DEFAULT NULL,
  `url`               VARCHAR(2048)   NOT NULL,
  `url_hash`          CHAR(64)        NOT NULL,
  `browser_folder_id` VARCHAR(128)    NULL DEFAULT NULL,
  `folder_path`       VARCHAR(512)    NULL DEFAULT NULL,
  `status`            VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / FETCHING / PARSING / CHUNKING / EMBEDDING / READY / FAILED / SKIPPED',
  `article_id`        BIGINT UNSIGNED NULL DEFAULT NULL,
  `error_code`        VARCHAR(64)     NULL DEFAULT NULL,
  `error_message`     VARCHAR(1024)   NULL DEFAULT NULL,
  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bookmark_items_import_url` (`import_id`, `url_hash`),
  KEY `idx_bookmark_items_import_status` (`import_id`, `status`),
  KEY `idx_bookmark_items_user_status` (`user_id`, `status`),
  KEY `idx_bookmark_items_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='书签导入条目';

CREATE TABLE IF NOT EXISTS `file_assets` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `storage_type`  VARCHAR(16)     NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL / OSS / R2',
  `bucket`        VARCHAR(128)    NULL DEFAULT NULL,
  `object_key`    VARCHAR(512)    NOT NULL,
  `original_name` VARCHAR(255)    NOT NULL,
  `mime_type`     VARCHAR(128)    NOT NULL,
  `size_bytes`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `sha256`        CHAR(64)        NOT NULL,
  `status`        VARCHAR(16)     NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING / READY / FAILED / DELETED',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at`    DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_assets_user_sha` (`user_id`, `sha256`),
  KEY `idx_file_assets_user_status` (`user_id`, `status`),
  KEY `idx_file_assets_sha` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上传文件资产';

-- ------------------------------------------------------------
-- 3. 知识库
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `articles` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `team_id`       BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'NULL=个人；非空=同时共享到团队',
  `title`         VARCHAR(512)    NOT NULL,
  `url`           VARCHAR(2048)   NULL DEFAULT NULL,
  `url_hash`      CHAR(64)        NULL DEFAULT NULL,
  `domain`        VARCHAR(255)    NULL DEFAULT NULL,
  `source_type`   VARCHAR(16)     NOT NULL COMMENT 'BOOKMARK / MANUAL / UPLOAD',
  `author`        VARCHAR(255)    NULL DEFAULT NULL,
  `published_at`  DATETIME(3)     NULL DEFAULT NULL,
  `fetched_at`    DATETIME(3)     NULL DEFAULT NULL,
  `summary`       TEXT            NULL DEFAULT NULL,
  `content_text`  MEDIUMTEXT      NULL DEFAULT NULL,
  `content_hash`  CHAR(64)        NULL DEFAULT NULL,
  `word_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
  `language`      VARCHAR(16)     NULL DEFAULT NULL,
  `status`        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PARSING / CHUNKING / EMBEDDING / READY / FAILED',
  `file_asset_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at`    DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_articles_user_url` (`user_id`, `url_hash`),
  KEY `idx_articles_user_status_updated` (`user_id`, `status`, `updated_at`),
  KEY `idx_articles_team_status_updated` (`team_id`, `status`, `updated_at`),
  KEY `idx_articles_user_content_hash` (`user_id`, `content_hash`),
  KEY `idx_articles_user_domain` (`user_id`, `domain`),
  KEY `idx_articles_file_asset` (`file_asset_id`),
  KEY `idx_articles_deleted` (`deleted_at`),
  FULLTEXT KEY `ft_articles_title_content` (`title`, `content_text`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料';

CREATE TABLE IF NOT EXISTS `article_chunks` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `article_id`      BIGINT UNSIGNED NOT NULL,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `chunk_index`     INT UNSIGNED    NOT NULL,
  `content`         TEXT            NOT NULL,
  `token_count`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `char_start`      INT UNSIGNED    NOT NULL DEFAULT 0,
  `char_end`        INT UNSIGNED    NOT NULL DEFAULT 0,
  `embedding_model` VARCHAR(64)     NOT NULL,
  `embedding_dim`   SMALLINT UNSIGNED NOT NULL DEFAULT 1024,
  `vector_id`       VARCHAR(128)    NOT NULL,
  `content_hash`    CHAR(64)        NULL DEFAULT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chunks_article_index` (`article_id`, `chunk_index`),
  KEY `idx_chunks_user_article` (`user_id`, `article_id`),
  KEY `idx_chunks_vector` (`vector_id`),
  FULLTEXT KEY `ft_chunks_content` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料切片';

CREATE TABLE IF NOT EXISTS `article_folders` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `article_id`         BIGINT UNSIGNED NOT NULL,
  `user_id`            BIGINT UNSIGNED NOT NULL,
  `bookmark_folder_id` BIGINT UNSIGNED NOT NULL,
  `folder_path`        VARCHAR(512)    NULL DEFAULT NULL,
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_folders` (`article_id`, `bookmark_folder_id`),
  KEY `idx_article_folders_user_folder` (`user_id`, `bookmark_folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料-文件夹关系';

CREATE TABLE IF NOT EXISTS `tags` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `name`            VARCHAR(64)     NOT NULL,
  `normalized_name` VARCHAR(64)     NOT NULL,
  `color`           VARCHAR(16)     NULL DEFAULT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tags_user_name` (`user_id`, `normalized_name`),
  KEY `idx_tags_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签';

CREATE TABLE IF NOT EXISTS `article_tags` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `article_id` BIGINT UNSIGNED NOT NULL,
  `tag_id`     BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_tags` (`article_id`, `tag_id`),
  KEY `idx_article_tags_tag` (`tag_id`, `user_id`),
  KEY `idx_article_tags_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料-标签关系';

CREATE TABLE IF NOT EXISTS `ingest_tasks` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `article_id`      BIGINT UNSIGNED NULL DEFAULT NULL,
  `source_type`     VARCHAR(16)     NOT NULL COMMENT 'BOOKMARK / MANUAL / UPLOAD / REINDEX',
  `stage`           VARCHAR(16)     NOT NULL COMMENT 'FETCH / PARSE / CHUNK / EMBED / INDEX / ENRICH / DONE',
  `status`          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED / DEAD',
  `retry_count`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `max_retries`     INT UNSIGNED    NOT NULL DEFAULT 2,
  `idempotency_key` VARCHAR(128)    NOT NULL,
  `error_code`      VARCHAR(64)     NULL DEFAULT NULL,
  `error_message`   VARCHAR(1024)   NULL DEFAULT NULL,
  `started_at`      DATETIME(3)     NULL DEFAULT NULL,
  `finished_at`     DATETIME(3)     NULL DEFAULT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ingest_tasks_idem` (`idempotency_key`),
  KEY `idx_ingest_tasks_article_stage` (`article_id`, `stage`, `status`),
  KEY `idx_ingest_tasks_status_created` (`status`, `created_at`),
  KEY `idx_ingest_tasks_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='解析/索引任务';

-- ------------------------------------------------------------
-- 4. 问答
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversations` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `title`      VARCHAR(255)    NOT NULL,
  `scope_type` VARCHAR(16)     NOT NULL DEFAULT 'ALL' COMMENT 'ALL / ARTICLE / FOLDER / TAG / CURRENT',
  `scope_id`   BIGINT UNSIGNED NULL DEFAULT NULL,
  `model`      VARCHAR(64)     NULL DEFAULT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conversations_user_updated` (`user_id`, `updated_at`),
  KEY `idx_conversations_scope` (`user_id`, `scope_type`, `scope_id`),
  KEY `idx_conversations_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答会话';

CREATE TABLE IF NOT EXISTS `messages` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id`   BIGINT UNSIGNED NOT NULL,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `role`              VARCHAR(16)     NOT NULL COMMENT 'USER / ASSISTANT / SYSTEM',
  `content`           MEDIUMTEXT      NOT NULL,
  `model`             VARCHAR(64)     NULL DEFAULT NULL,
  `prompt_tokens`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `completion_tokens` INT UNSIGNED    NOT NULL DEFAULT 0,
  `request_id`        VARCHAR(64)     NULL DEFAULT NULL,
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED / ABORTED',
  `error_code`        VARCHAR(64)     NULL DEFAULT NULL,
  `first_token_ms`    INT UNSIGNED    NULL DEFAULT NULL,
  `latency_ms`        INT UNSIGNED    NULL DEFAULT NULL,
  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_messages_conversation` (`conversation_id`, `id`),
  KEY `idx_messages_user_created` (`user_id`, `created_at`),
  KEY `idx_messages_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答消息';

CREATE TABLE IF NOT EXISTS `citations` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `article_id` BIGINT UNSIGNED NOT NULL,
  `chunk_id`   BIGINT UNSIGNED NOT NULL,
  `rank`       INT UNSIGNED    NOT NULL DEFAULT 1,
  `score`      DECIMAL(8,6)    NULL DEFAULT NULL,
  `quote`      VARCHAR(1024)   NULL DEFAULT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_citations_message_rank` (`message_id`, `rank`),
  KEY `idx_citations_article` (`article_id`),
  KEY `idx_citations_chunk` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回答引用';

CREATE TABLE IF NOT EXISTS `message_feedback` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `rating`     VARCHAR(8)      NOT NULL COMMENT 'UP / DOWN',
  `comment`    VARCHAR(500)    NULL DEFAULT NULL,
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_feedback` (`message_id`, `user_id`),
  KEY `idx_message_feedback_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回答反馈';

-- ------------------------------------------------------------
-- 5. AI 用量与配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_usage` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id`        VARCHAR(64)     NOT NULL,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `team_id`           BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '团队空间调用时的团队',
  `source`            VARCHAR(16)     NOT NULL COMMENT 'WEB / EXTENSION / ADMIN / API',
  `scene`             VARCHAR(16)     NOT NULL COMMENT 'CHAT / EMBEDDING / RERANK / SUMMARY / EVAL',
  `model`             VARCHAR(64)     NOT NULL,
  `prompt_tokens`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `completion_tokens` INT UNSIGNED    NOT NULL DEFAULT 0,
  `total_tokens`      INT UNSIGNED    NOT NULL DEFAULT 0,
  `cached_tokens`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `input_price`       DECIMAL(12,6)   NOT NULL DEFAULT 0,
  `output_price`      DECIMAL(12,6)   NOT NULL DEFAULT 0,
  `cost`              DECIMAL(14,8)   NOT NULL DEFAULT 0,
  `currency`          CHAR(3)         NOT NULL DEFAULT 'CNY',
  `latency_ms`        INT UNSIGNED    NOT NULL DEFAULT 0,
  `first_token_ms`    INT UNSIGNED    NULL DEFAULT NULL,
  `status`            VARCHAR(16)     NOT NULL COMMENT 'SUCCESS / FAILED / ABORTED',
  `error_code`        VARCHAR(64)     NULL DEFAULT NULL,
  `estimated`         TINYINT(1)      NOT NULL DEFAULT 0,
  `conversation_id`   BIGINT UNSIGNED NULL DEFAULT NULL,
  `article_id`        BIGINT UNSIGNED NULL DEFAULT NULL,
  `client_version`    VARCHAR(32)     NULL DEFAULT NULL,
  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_request` (`request_id`),
  KEY `idx_ai_usage_user_created` (`user_id`, `created_at`),
  KEY `idx_ai_usage_team_created` (`team_id`, `created_at`),
  KEY `idx_ai_usage_source_created` (`source`, `created_at`),
  KEY `idx_ai_usage_scene_created` (`scene`, `created_at`),
  KEY `idx_ai_usage_model_created` (`model`, `created_at`),
  KEY `idx_ai_usage_status_created` (`status`, `created_at`),
  KEY `idx_ai_usage_conversation` (`conversation_id`),
  KEY `idx_ai_usage_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用明细';

CREATE TABLE IF NOT EXISTS `team_usage_monthly` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`    BIGINT UNSIGNED NOT NULL,
  `month`      CHAR(7)         NOT NULL COMMENT 'YYYY-MM',
  `tokens`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `cost`       DECIMAL(14,8)   NOT NULL DEFAULT 0,
  `calls`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_usage_monthly` (`team_id`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队月度用量';

CREATE TABLE IF NOT EXISTS `ai_usage_daily` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `date`           DATE            NOT NULL,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `source`         VARCHAR(16)     NOT NULL,
  `scene`          VARCHAR(16)     NOT NULL,
  `model`          VARCHAR(64)     NOT NULL,
  `calls`          INT UNSIGNED    NOT NULL DEFAULT 0,
  `tokens`         BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `cost`           DECIMAL(14,8)   NOT NULL DEFAULT 0,
  `currency`       CHAR(3)         NOT NULL DEFAULT 'CNY',
  `avg_latency_ms` INT UNSIGNED    NOT NULL DEFAULT 0,
  `error_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage_daily` (`date`, `user_id`, `source`, `scene`, `model`),
  KEY `idx_usage_daily_user_date` (`user_id`, `date`),
  KEY `idx_usage_daily_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 用量日汇总';

CREATE TABLE IF NOT EXISTS `user_usage_monthly` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `month`      CHAR(7)         NOT NULL COMMENT 'YYYY-MM',
  `tokens`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `cost`       DECIMAL(14,8)   NOT NULL DEFAULT 0,
  `calls`      INT UNSIGNED    NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_usage_monthly` (`user_id`, `month`),
  KEY `idx_user_usage_monthly_month` (`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户月用量（配额）';

CREATE TABLE IF NOT EXISTS `model_prices` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider`      VARCHAR(64)     NOT NULL,
  `model`         VARCHAR(128)    NOT NULL,
  `scene`         VARCHAR(16)     NOT NULL COMMENT 'CHAT / EMBEDDING / RERANK / SUMMARY / EVAL',
  `input_price`   DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每 1M 输入 token',
  `output_price`  DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每 1M 输出 token',
  `currency`      CHAR(3)         NOT NULL DEFAULT 'CNY',
  `effective_at`  DATETIME(3)     NOT NULL,
  `is_active`     TINYINT(1)      NOT NULL DEFAULT 1,
  `created_by`    BIGINT UNSIGNED NULL DEFAULT NULL,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_model_prices_lookup` (`provider`, `model`, `scene`, `effective_at`),
  KEY `idx_model_prices_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型价格';

CREATE TABLE IF NOT EXISTS `system_settings` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `setting_key`   VARCHAR(128)    NOT NULL,
  `setting_value` JSON            NULL DEFAULT NULL,
  `is_secret`     TINYINT(1)      NOT NULL DEFAULT 0,
  `description`   VARCHAR(255)    NULL DEFAULT NULL,
  `updated_by`    BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'admin_users.id',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_settings_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局配置';

CREATE TABLE IF NOT EXISTS `admin_audit_logs` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`      BIGINT UNSIGNED NOT NULL,
  `action`        VARCHAR(64)     NOT NULL,
  `resource_type` VARCHAR(64)     NOT NULL,
  `resource_id`   VARCHAR(64)     NULL DEFAULT NULL,
  `detail`        JSON            NULL DEFAULT NULL,
  `ip`            VARCHAR(45)     NULL DEFAULT NULL,
  `user_agent`    VARCHAR(512)    NULL DEFAULT NULL,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_admin_time` (`admin_id`, `created_at`),
  KEY `idx_audit_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员审计日志';

-- ------------------------------------------------------------
-- 6. RAG 评测（P1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rag_eval_cases` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`              BIGINT UNSIGNED NOT NULL,
  `question`             TEXT            NOT NULL,
  `expected_article_ids` JSON            NULL DEFAULT NULL,
  `expected_chunk_ids`   JSON            NULL DEFAULT NULL,
  `gold_answer`          TEXT            NULL DEFAULT NULL,
  `tags`                 JSON            NULL DEFAULT NULL,
  `status`               VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
  `created_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_eval_cases_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 评测用例';

CREATE TABLE IF NOT EXISTS `rag_eval_runs` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `name`            VARCHAR(128)    NOT NULL,
  `model`           VARCHAR(64)     NULL DEFAULT NULL,
  `embedding_model` VARCHAR(64)     NULL DEFAULT NULL,
  `top_k`           INT UNSIGNED    NULL DEFAULT NULL,
  `rerank`          TINYINT(1)      NOT NULL DEFAULT 0,
  `metrics`         JSON            NULL DEFAULT NULL,
  `started_at`      DATETIME(3)     NULL DEFAULT NULL,
  `finished_at`     DATETIME(3)     NULL DEFAULT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_eval_runs_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 评测运行';

CREATE TABLE IF NOT EXISTS `rag_eval_results` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id`            BIGINT UNSIGNED NOT NULL,
  `case_id`           BIGINT UNSIGNED NOT NULL,
  `retrieved_chunk_ids` JSON          NULL DEFAULT NULL,
  `recall_at_5`       DECIMAL(6,4)    NULL DEFAULT NULL,
  `citation_accuracy` DECIMAL(6,4)    NULL DEFAULT NULL,
  `answer_correctness` DECIMAL(6,4)   NULL DEFAULT NULL,
  `answer_text`       TEXT            NULL DEFAULT NULL,
  `judge_reason`      TEXT            NULL DEFAULT NULL,
  `latency_ms`        INT UNSIGNED    NULL DEFAULT NULL,
  `cost`              DECIMAL(14,8)   NULL DEFAULT NULL,
  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_eval_results_run` (`run_id`),
  KEY `idx_eval_results_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 评测结果';

-- ------------------------------------------------------------
-- 7. 幂等
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `idempotency_keys` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `endpoint`        VARCHAR(128)    NOT NULL,
  `idempotency_key` VARCHAR(128)    NOT NULL,
  `request_hash`    CHAR(64)        NOT NULL,
  `response_code`   INT UNSIGNED    NULL DEFAULT NULL,
  `response_body`   JSON            NULL DEFAULT NULL,
  `expires_at`      DATETIME(3)     NOT NULL,
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency` (`user_id`, `endpoint`, `idempotency_key`),
  KEY `idx_idempotency_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='POST 幂等键';
