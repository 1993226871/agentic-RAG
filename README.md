# Agentic RAG

一个完整的 RAG 示例项目，覆盖从文件入库到在线问答的全流程，并在问答侧引入 Agentic ReAct（可迭代检索）能力。

适合用于：

- 学习 RAG 工程化落地（上传、解析、向量化、检索、重排）
- 本地搭建一套可运行的知识库问答系统
- 二次开发 Agent 工具化检索、记忆摘要、多轮会话

## 这个项目做了什么

项目包含两条主链路：

1. 离线上传入库链路
   - 前端分片上传
   - Redis Bitmap 记录分片状态（支持断点续传）
   - MinIO 保存分片并合并文件
   - RocketMQ 发送上传完成消息
   - 异步消费后执行：Tika 解析 -> 文本分块 -> Embedding -> 写入 Elasticsearch

2. 在线问答链路
   - 用户问题进入 Agent/ReAct 决策循环
   - 混合检索：向量检索（KNN）+ 关键词检索（BM25）
   - RRF 融合多路检索结果
   - Rerank 进行二次排序
   - 输出最终答案和检索上下文

另外支持：

- 多用户隔离（`userId + fileMd5` 逻辑隔离）
- 会话记忆摘要（达到阈值或结束会话后入库）
- 可配置模型（百炼 Embedding、Rerank、Agent 规划）

## 技术栈

- 后端：Java 8、Spring Boot 2.7、Maven
- 存储与中间件：Redis、MinIO、RocketMQ、Elasticsearch 7.17（IK 分词）
- 文档处理：Apache Tika
- 向量与大模型：DashScope（Qwen）
- Agent 编排：LangChain4j（Planner）
- 前端：原生 HTML/CSS/JS（`portal.html`、`self-test.html`）
- 脚本与测试：PowerShell、JUnit 5

## 项目结构（核心模块）

- `src/main/java/com/agenticrag/api`：HTTP 接口层
- `src/main/java/com/agenticrag/service`：业务编排层
  - `OfflineUploadService`：上传链路
  - `AsyncIngestionConsumer`：异步入库链路
  - `OnlineQaService`：检索与问答基础能力
  - `ReActAgentService`：Agent 迭代决策
- `src/main/java/com/agenticrag/infra`：基础设施适配层
  - `embedding`、`rerank`、`es`、`rocketmq`、`minio`、`redis`
- `src/main/java/com/agenticrag/ports`：端口接口定义
- `src/main/resources/application.yml`：主要配置
- `scripts`：自测与辅助脚本

## 运行模式

- `mock`：轻依赖模式，便于本地快速开发和测试
- `real`：真实中间件模式（默认）

当前默认配置为真实模式：`rag.mock-enabled: false`

## 环境准备

建议环境：

- JDK 8
- Maven 3.8+
- Docker / Docker Compose
- Windows PowerShell（脚本使用）

## 快速上手（推荐流程）

### 1) 启动中间件

```bash
docker compose up -d --build
```

默认端口：

- Redis: `6379`
- MinIO: `9000`（Console `9001`）
- Elasticsearch: `9200`
- RocketMQ NameServer: `9876`

### 2) 配置模型 Key

项目默认从环境变量读取百炼 Key：

- `DASHSCOPE_API_KEY`

例如（Windows）：

```powershell
setx DASHSCOPE_API_KEY "your_api_key"
```

> 配置后请重新打开终端再启动服务。

### 3) 启动项目

```bash
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

### 4) 打开页面体验

- 业务页面：`http://localhost:8080/portal.html`
- 自测页面：`http://localhost:8080/self-test.html`

默认用户：`root / 123456`

## 核心配置说明

配置文件：`src/main/resources/application.yml`

常用配置项：

- `rag.embedding.*`：Embedding 模型与接口
- `rag.rerank.*`：Rerank 模型与接口
- `rag.rewrite.*`：Query Rewrite
- `rag.agent.*`：ReAct Agent（开关、最大步数、规划模型）
- `rag.memory.*`：会话记忆摘要与检索

## 常用接口

### 上传相关

- `POST /api/upload/init`
- `POST /api/upload/chunk`
- `GET /api/upload/status`

### 问答相关

- `POST /api/qa/ask`
- `POST /api/qa/ask-scoped`
- `GET /api/qa/status-scoped`
- `POST /api/qa/end-session`

### 认证

- `POST /api/auth/login`
- `POST /api/auth/register`

## 本地自测

### 单元/集成测试

```bash
mvn test
```

### 真实链路 E2E

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\e2e-real-check.ps1
```

### 手工请求集合

- `scripts/self-test.http`

## 常见问题

### 1) Spring Boot 启动失败，提示 8080 被占用

关闭占用进程，或改 `server.port`。

### 2) Elasticsearch 建索引失败，提示 IK 相关错误

确认 ES 使用了带 IK 的镜像（本项目 `docker-compose.yml` 已处理）。

### 3) 上传后无法检索到内容

优先检查：

- RocketMQ 是否正常消费上传消息
- Elasticsearch 中 `rag_chunks` 是否有数据
- 问答使用的 `userId/fileMd5` 是否与上传一致

## 许可证

Apache-2.0
