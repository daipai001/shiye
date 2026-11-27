#!/usr/bin/env bash
# 生产服务器预检：只读，不修改任何容器/端口/配置。
# 在宝塔终端或 SSH 执行后把输出发回即可。
set -euo pipefail

echo "== system =="
uname -a || true
free -m || true
df -h / || true

echo "== docker =="
docker --version || true
docker compose version || true

echo "== running containers =="
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}' || true

echo "== listening ports =="
if command -v ss >/dev/null 2>&1; then
  ss -lntp || true
else
  netstat -lntp || true
fi

echo "== disk /opt =="
du -sh /opt/* 2>/dev/null | sort -h | tail -20 || true

echo "== DNS =="
getent hosts shiye.myhyw.asia || true
getent hosts admin.shiye.myhyw.asia || true
getent hosts myhyw.asia || true

echo "== HTTPS =="
for url in https://shiye.myhyw.asia https://admin.shiye.myhyw.asia; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "$url" 2>/dev/null || true)
  echo "$url -> ${code:-failed}"
done
