-- 清理升级前已被移除成员留下的未使用邀请码。
-- 只删除邀请人当前不是该团队 ACTIVE 成员的记录。
DELETE ti
FROM `team_invites` ti
JOIN `teams` t ON t.`id` = ti.`team_id` AND t.`deleted_at` IS NULL
WHERE ti.`used_at` IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `team_members` tm
    WHERE tm.`team_id` = ti.`team_id`
      AND tm.`user_id` = ti.`inviter_user_id`
      AND tm.`status` = 'ACTIVE'
  );
