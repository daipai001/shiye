-- 团队操作独立审计流水：邀请、加入、移除、退出、角色变更、转让、解散、资料共享。
-- 仅追加，不提供修改/删除接口；管理员通过 admin-api 查询。
CREATE TABLE IF NOT EXISTS `team_audit_logs` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `team_id`     BIGINT UNSIGNED NOT NULL,
  `operator_id` BIGINT UNSIGNED NOT NULL,
  `action`      VARCHAR(64)     NOT NULL,
  `target_type` VARCHAR(32)     NULL DEFAULT NULL,
  `target_id`   VARCHAR(64)     NULL DEFAULT NULL,
  `detail`      JSON            NULL DEFAULT NULL,
  `ip`          VARCHAR(45)     NULL DEFAULT NULL,
  `user_agent`  VARCHAR(512)    NULL DEFAULT NULL,
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_team_audit_team` (`team_id`, `id`),
  KEY `idx_team_audit_operator` (`operator_id`, `id`),
  KEY `idx_team_audit_action` (`action`, `id`),
  KEY `idx_team_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队操作审计';
