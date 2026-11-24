# ADR-0003：RAG 与可插拔会话记忆

- 状态：已采纳
- 日期：2026-11-24

## 背景

v0.2 之后 Agent 已经能流式回答、并行调用工具，但有两个明显短板：

1. 会话记忆只在进程内，重启即失，多实例无法共享；
2. 模型只掌握内置工具与自身知识，无法回答"项目文档里写了什么"。

v0.3 引入 **Redis 会话记忆**与**最小可用的 RAG 流水线**。

## 决策

### 1. 记忆是接口，默认进程内，Redis 按配置启用

`ChatMemory` 保持两个实现：`InMemoryChatMemory`（零依赖默认）与 `RedisChatMemory`（列表 + JSON + TTL）。
`agent.memory.type` 选择实现；Redis 不可用时回退到进程内并输出 WARN。

理由：本地开发与测试不应被 Redis 绑架；生产/多实例部署又需要共享记忆与过期策略。JSON 存储让
`redis-cli LRANGE` 可读，排障成本低。

### 2. 检索链路：切块 → embedding → 向量检索 → 工具

- **切块**：字符级、带重叠、优先段落与句子边界。中文不需要分词依赖，行为可预测。
- **Embedding**：`EmbeddingClient` 接缝。配置了 `EMBEDDING_API_KEY` 时走任意 OpenAI 兼容 `/embeddings`；
  没配置时用**离线 hashing embedding**（字符 bigram 哈希 + L2 归一化）。后者只做词面匹配，
  明确标注为"演示/离线"，避免假装语义检索。
- **向量存储**：`VectorStore` 接缝，默认 `InMemoryVectorStore`（余弦相似度）。
  规模与持久化需求上来时换 pgvector/Qdrant，不影响知识服务与 Agent 工具。
- **工具化**：`knowledge_search` 作为普通 `Tool` Bean 注册，模型在需要内部资料时自行调用；
  同时暴露 `/api/v1/knowledge` REST 端点用于调试与手工检索。

### 3. 为什么暂不直接上 pgvector

本阶段目标是**链路完整且可测试**：切块、embedding、相似度、工具调用、HTTP 端点都能在无外部依赖下运行与断言。
pgvector 的价值在持久化与规模化，等语料、并发或重启需求真实出现时再引入，避免过早绑定部署形态。

## 代价与边界

- 离线 hashing embedding 只能词面匹配，同义改写检索不到；生产必须配真实 embedding。
- `InMemoryVectorStore` 无持久化、无近似索引，重启清空，适合演示与中小语料。
- Redis 记忆以"一个会话一个 list"存储，超长会话靠 trim 控制，没有做摘要压缩（留给 v0.4 的 compaction）。

## 何时重新考虑

- 语料 > 10 万片段或需要多实例共享向量：引入 pgvector/Qdrant + 近似索引；
- 需要多轮长对话：记忆改为摘要 + 窗口混合，而不是简单截断。
