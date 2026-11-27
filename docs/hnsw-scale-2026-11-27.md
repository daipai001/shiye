# 2026-11-27 HNSW 规模测试

## 环境

- Redis Stack 7.4.7（RediSearch 2.10.20），Docker Desktop，容器内存上限 7.7GB。
- 向量维度 1024，FLOAT32，COSINE，HNSW。
- 测试工具：`scripts/hnsw-scale-test.mjs`（RESP2，`redis-cli --pipe` 写入，避免 Node 内存膨胀）。
- 本机为开发机，不是生产服务器；生产请在自己的机器上复跑。

## 结果

| 切片数 | 写入耗时 | 索引重建 | Redis 内存 | 查询 P50 | 查询 P95 | 空结果 |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 1.28s | 1.07s | 12.99MB | 0.84ms | 1.89ms | 0 |
| 100,000 | 437s（约 7.3 分钟） | 347s（约 5.8 分钟） | 1.05GB | 3.08ms | 4.83ms | 0 |

100,000 条测试中：

- `numDocs=100000`，`emptyQueries=0`。
- 查询使用 `@team_id` + `embedding_model` 过滤，topK=10。
- 索引重建通过 `FT.DROPINDEX` + `FT.CREATE`，Hash 数据保留。
- 写入耗时包含 HNSW 同步索引时间，因此索引完成后 `indexing=0`。

## 对 4GB 服务器的结论

按 100,000 条约 1.05GB Redis 内存线性估算：

| 切片数 | 预计 Redis 内存 | 预计重建时间 |
|---:|---:|---:|
| 100,000 | 约 1.05GB | 约 6 分钟 |
| 300,000 | 约 3.2GB | 约 18 分钟 |
| 1,000,000 | 约 10.5GB | 约 58 分钟 |

服务器规格是 2 vCPU / 4GB，且已有 offerdesk 的 MySQL/Redis/backend 常驻内存。结论：

- **不建议在这台 4GB 服务器上启用 100 万切片 HNSW**；仅 Redis 就需要约 10GB。
- 4GB 服务器建议 HNSW 控制在 **3 万～5 万切片**以内，或继续使用默认 Hash pipeline。
- 如果数据量到 10 万+，建议升级到 8GB 以上，或把 Redis Stack 拆到独立实例并设置 `maxmemory`。
- 100 万切片需要至少 16GB 内存，并预留索引重建窗口（约 1 小时）。

## 复跑

```bash
HNSW_TEST_FLUSH=yes \
REDIS_HOST=127.0.0.1 REDIS_PORT=16380 \
COUNT=100000 DIM=1024 QUERIES=100 \
node scripts/hnsw-scale-test.mjs
```

脚本会 `FLUSHDB`，只能对专用 Redis 测试实例执行。生产 Redis 不要直接跑。
