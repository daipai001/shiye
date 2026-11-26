CREATE TABLE IF NOT EXISTS `global_usage_monthly` (
  `month`      CHAR(7)         NOT NULL,
  `tokens`     BIGINT          NOT NULL DEFAULT 0,
  `cost`       DECIMAL(18,8)   NOT NULL DEFAULT 0,
  `calls`      BIGINT          NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全站月度 AI 用量与预算预留';

INSERT INTO `global_usage_monthly` (`month`, `tokens`, `cost`, `calls`)
SELECT `month`, COALESCE(SUM(`tokens`), 0), COALESCE(SUM(`cost`), 0), COALESCE(SUM(`calls`), 0)
FROM `user_usage_monthly`
GROUP BY `month`
ON DUPLICATE KEY UPDATE
  `tokens` = VALUES(`tokens`),
  `cost` = VALUES(`cost`),
  `calls` = VALUES(`calls`);
