CREATE TABLE IF NOT EXISTS `usage_reservations` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `team_id`      BIGINT UNSIGNED NULL DEFAULT NULL,
  `month`        CHAR(7)         NOT NULL,
  `user_tokens`  BIGINT          NOT NULL DEFAULT 0,
  `user_cost`    DECIMAL(18,8)   NOT NULL DEFAULT 0,
  `team_tokens`  BIGINT          NOT NULL DEFAULT 0,
  `team_cost`    DECIMAL(18,8)   NOT NULL DEFAULT 0,
  `global_tokens` BIGINT         NOT NULL DEFAULT 0,
  `global_cost`  DECIMAL(18,8)   NOT NULL DEFAULT 0,
  `status`       VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
  `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at`   DATETIME(3)     NOT NULL,
  `released_at`  DATETIME(3)     NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_usage_reservations_reap` (`status`, `expires_at`),
  KEY `idx_usage_reservations_user` (`user_id`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问答配额预留流水';
