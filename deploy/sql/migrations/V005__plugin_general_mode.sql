-- 通用问答模式已开放，恢复 default_mode = GENERAL 的合法值说明。
ALTER TABLE `user_plugin_settings`
  MODIFY COLUMN `default_mode` VARCHAR(16) NOT NULL DEFAULT 'LIBRARY'
  COMMENT 'LIBRARY / CURRENT / GENERAL';
