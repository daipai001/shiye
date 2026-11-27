-- 回滚：先回滚到不读/写 global_usage_monthly 的版本，再执行 DROP TABLE `global_usage_monthly`;
CREATE TABLE IF NOT EXISTS `global_usage_monthly` (
  `month`      CHAR(7)         NOT NULL,
  `tokens`     BIGINT          NOT NULL DEFAULT 0,
  `cost`       DECIMAL(18,8)   NOT NULL DEFAULT 0,
  `calls`      BIGINT          NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全站月度 AI 用量与预算预留';

-- 只补缺失月份，避免迁移重跑时覆盖已经包含预留的实时计数。
INSERT INTO `global_usage_monthly` (`month`, `tokens`, `cost`, `calls`)
SELECT u.`month`, COALESCE(SUM(u.`tokens`), 0), COALESCE(SUM(u.`cost`), 0),
       COALESCE(SUM(u.`calls`), 0)
FROM `user_usage_monthly` u
WHERE NOT EXISTS (
  SELECT 1 FROM `global_usage_monthly` g WHERE g.`month` = u.`month`
)
GROUP BY u.`month`;
