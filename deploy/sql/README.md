# 数据库初始化

## 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS shiye
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE shiye;
```

## 2. 执行表结构

```bash
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/schema.sql
```

完整字段说明见 `docs/schema.md`；初始迁移为 `deploy/sql/migrations/V001__init.sql`，后续迁移按顺序执行：V002–V005、`2026-11-20-team-space.sql`、`2026-11-26-global-usage.sql`、`2026-11-26-invite-cleanup.sql`、`2026-11-26-eval-progress.sql`、`2026-11-27-usage-reservations.sql`、`2026-11-27-team-audit.sql`。

## 3. 初始化管理员

```bash
mysql -uroot -p --default-character-set=utf8mb4 shiye < deploy/sql/seed.sql
```

默认账号：`admin@shiye.local`

初始密码：见项目负责人的私密记录，不写入仓库；首次登录后必须修改。

## 4. 重置管理员密码

用 BCrypt 生成新哈希（cost=10），替换 `admin_users.password_hash`。需要本地有 `bcryptjs`（`npm install bcryptjs`）：

```bash
node -e "console.log(require('bcryptjs').hashSync('新密码', 10))"
```

```sql
UPDATE admin_users
SET password_hash = '新的 BCrypt 哈希'
WHERE email = 'admin@shiye.local';
```

> 不要把明文密码、`.env`、`application-local.yml` 提交到仓库。
