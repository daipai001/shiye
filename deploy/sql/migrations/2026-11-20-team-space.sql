-- ============================================================
-- 拾页 ShiYe：团队空间迁移（v0.32.0+）
-- 适用于已经执行过旧版 schema.sql 的数据库；执行一次即可。
-- 新库直接执行 deploy/sql/schema.sql，不需要本文件。
-- ============================================================

SET NAMES utf8mb4;

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

ALTER TABLE `articles`
  ADD COLUMN `team_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT 'NULL=个人；非空=同时共享到团队' AFTER `user_id`,
  ADD KEY `idx_articles_team_status_updated` (`team_id`, `status`, `updated_at`);

ALTER TABLE `ai_usage`
  ADD COLUMN `team_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '团队空间调用时的团队' AFTER `user_id`,
  ADD KEY `idx_ai_usage_team_created` (`team_id`, `created_at`);
