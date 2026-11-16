-- ============================================================
-- 拾页 ShiYe：初始化后台超级管理员
-- 执行前先执行 schema.sql，并选择数据库：USE shiye;
-- 默认账号：admin@shiye.local
-- 初始密码：见项目负责人的私密记录，不写入仓库；首次登录后必须修改
-- 仓库只保存 BCrypt 哈希，不保存明文密码
-- ============================================================

SET NAMES utf8mb4;

-- 如需重置密码：用 BCrypt 生成新哈希，替换下面的 password_hash
INSERT IGNORE INTO `admin_users` (`email`, `password_hash`, `name`, `role`, `status`)
VALUES (
  'admin@shiye.local',
  '$2b$10$ah5K3t2ccVSZyvgohoJe6Odtivp/4BvJnP8L0D1Gg/2BeO8i0eD4a',
  '拾页管理员',
  'SUPER_ADMIN',
  'ACTIVE'
);
