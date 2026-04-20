# Agentic RAG

该项目实现了你要求的两条链路，并支持 `mock` 与 `real` 两种运行模式：

- 离线上传链路：分片上传 -> Redis Bitmap 记录状态 -> MinIO 合并 -> RocketMQ 异步消息
- 异步入库链路：Tika 解析 -> 文本分块 -> 阿里 Embedding（可回退 mock）-> Elasticsearch 存储
- 在线问答链路：KNN（向量相似）+ BM25 双路召回 -> RRF 融合 -> BGE rerank（可回退 mock）

## 运行模式

- `mock`：不依赖外部中间件，适合本地开发和自动化测试
- `real`（默认）：连接 Redis / MinIO / RocketMQ / Elasticsearch

当前默认已切换为 `real`（`rag.mock-enabled: false`），即 RocketMQ 自动消费。

切换到真实模式：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=real
```

## 启动中间件（真实模式）

```bash
docker compose up -d
```

如果你在上传时看到 `sendDefaultImpl call timeout`，通常是 RocketMQ broker 对主机不可达。  
本项目已内置 `docker/rocketmq/broker.conf` 并设置 `brokerIP1=127.0.0.1`，请重新创建容器使配置生效：

```bash
docker compose down
docker compose up -d --build
```

包含服务：

- Redis: `localhost:6379`
- MinIO: `localhost:9000`（console: `localhost:9001`）
- Elasticsearch: `localhost:9200`
- RocketMQ NameServer: `localhost:9876`

> 注意：ES 已配置 IK 分词（`ik_max_word` / `ik_smart`）。请确认你的 ES 安装了 IK 插件，否则索引创建会失败。
> 本项目的 `docker-compose.yml` 已改为自动构建带 IK 的 ES 镜像（首次启动会稍慢）。

## 启动 BGE Reranker（本地服务）

项目内已提供一个可直接对接 `BgeHttpReranker` 的 HTTP 服务：

- `scripts/reranker-server.py`
- `scripts/requirements-reranker.txt`

启动步骤：

```bash
python -m venv .venv-reranker
.venv-reranker\Scripts\activate
pip install -r scripts/requirements-reranker.txt
uvicorn scripts.reranker-server:app --host 0.0.0.0 --port 8001
```

Windows 一键启动（推荐）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-reranker.ps1
```

指定模型启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-reranker.ps1 -Model "BAAI/bge-reranker-large"
```

默认模型：`BAAI/bge-reranker-base`（可用环境变量 `BGE_RERANK_MODEL` 覆盖）

健康检查：

```bash
curl http://127.0.0.1:8001/health
```

项目中默认已配置：

- `rag.rerank.endpoint: http://127.0.0.1:8001/rerank`

## HTTP 接口

### 1) 初始化上传任务

`POST /api/upload/init`

```json
{
  "fileId": "doc-001",
  "totalChunks": 3
}
```

### 2) 上传分片

`POST /api/upload/chunk`（multipart/form-data）

字段：

- `fileId`
- `totalChunks`
- `chunkIndex`
- `file`

### 3) 查询分片状态

`GET /api/upload/status?fileId=doc-001&chunkIndex=1`

### 4) 在线问答

`POST /api/qa/ask`

```json
{
  "query": "RAG 为什么要同时使用 BM25 和向量检索",
  "topK": 3
}
```

## 模块测试与测试数据

已覆盖并通过：

- `OfflineUploadServiceTest`：断点续传、分片状态、合并触发、消息发送
- `AsyncIngestionConsumerTest`：解析、分块、向量化、索引落库
- `OnlineQaServiceTest`：KNN + BM25 + RRF + rerank
- `EndToEndRagFlowTest`：离线上传到在线问答全链路
- `RealAdapterFallbackTest`：阿里 embedding 与 BGE reranker 的真实接口回退策略

运行测试：

```bash
mvn test
```

## 手工自测指南

### 方式A：浏览器可视化自测台（最推荐）

启动服务后打开：

- `http://localhost:8080/self-test.html`

页面可直接操作完整链路：

- 健康检查
- 选择文件并自动分片上传
- 查询分片上传状态
- mock 模式下触发一次异步消费
- 在线问答验证

### 业务门户（多用户隔离）

访问：

- `http://localhost:8080/portal.html`

功能：

- 用户登录/注册（默认 `root / 123456`）
- 文件分片上传
- 浏览器端计算文件 MD5
- 使用 `userId:md5` 作为隔离 `fileId`
- 作用域问答接口：`/api/qa/ask-scoped`（只查当前用户+当前文件）
- 前端消息提示采用弹出 `message window`（不再使用底部日志框）

### 方式B：PowerShell一键自测

```powershell
# mock模式（默认）
powershell -ExecutionPolicy Bypass -File .\scripts\self-test.ps1 -Mode mock -FilePath .\sample-data\rag-intro.txt

# real模式
powershell -ExecutionPolicy Bypass -File .\scripts\self-test.ps1 -Mode real -FilePath .\sample-data\upload-resume.txt
```

### 方式C：HTTP请求文件

- 使用 `scripts/self-test.http` 逐条执行请求
- 适合你在 IDE HTTP Client / Apifox / Postman 中对照调试

## 额外说明

- `mock` 模式下，消息消费不会自动触发，需要调用 `POST /api/admin/consume-once`
- `real` 模式下，由 RocketMQ 监听器自动消费上传完成消息，无需手动调用
