-- 插件默认问答范围支持指定文件夹或标签。
-- GENERAL 模式暂不开放，历史值由应用层归一为 LIBRARY。
ALTER TABLE `user_plugin_settings`
  ADD COLUMN `default_scope_id` BIGINT UNSIGNED NULL DEFAULT NULL
  COMMENT 'FOLDER/TAG 默认范围 ID' AFTER `default_scope`;

ALTER TABLE `user_plugin_settings`
  MODIFY COLUMN `default_mode` VARCHAR(16) NOT NULL DEFAULT 'LIBRARY'
  COMMENT 'LIBRARY / CURRENT';
