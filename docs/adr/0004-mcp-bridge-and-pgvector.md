# ADR-0004：MCP 工具桥与可切换的向量存储

- 状态：已采纳
- 日期：2026-11-24

## 背景

v0.3 的工具体系是进程内的：`Tool` Bean 由 Spring 扫描注册。但 MCP（Model Context Protocol）正在成为
外部工具的通用协议，Agent 需要能"接入一个现成的 MCP server"而不是为每个工具写适配代码。
同时，v0.3 的向量库是进程内的，重启即失，也无法跨实例共享。

v0.4 解决这两点：**MCP 工具桥**与**可选 pgvector 向量库**。

## 决策

### 1. MCP 客户端手写，不引 SDK

`StdioMcpClient` 直接用子进程的 stdin/stdout 实现 MCP 的 stdio 传输：

- 换行分隔的 JSON-RPC 2.0；请求 id ↔ `CompletableFuture` 关联；虚拟线程读取响应；
- 握手：`initialize` → `notifications/initialized` → `tools/list` → `tools/call`；
- 工具注册名 `mcp__<server>__<tool>`，来源可见、避免重名；
- 在 `ApplicationReadyEvent` 之后接入，单个 server 失败只记 WARN，不影响 HTTP 服务。

理由与 ADR-0001 一致：协议很小，手写让 wire format 可见、可测试；测试用一个 30 行的 Python
stdio server 覆盖真实握手与调用，不依赖网络与 SDK 版本。

### 2. 桥接是"注册普通 Tool"，不是新增执行路径

MCP 工具经 `McpToolAdapter` 适配为普通 `Tool` Bean 后进入同一个 `ToolRegistry`，因此 agent 循环、
并行执行、超时、流式、Mock 全部复用，不需要为 MCP 开第二条执行链路。

### 3. 向量存储是接缝，pgvector 按配置启用

`agent.rag.store=in-memory`（默认）或 `pgvector`。pgvector 实现用 JDBC + 余弦距离（`<=>`），
启动时建 `vector` 扩展与表；为保证默认启动零数据库依赖，应用排除了 `DataSourceAutoConfiguration`，
pgvector 模式下由配置类自建 `DriverManagerDataSource`。

存储与检索语义与内存实现一致（余弦相似度、过滤零分、topK），因此知识服务与 `knowledge_search`
无需感知后端差异。

## 代价与边界

- MCP 只实现 stdio 传输与 tools 能力（resources/prompts/HTTP 传输未实现）；重连与健康检查未做。
- MCP 子进程与主进程同权限运行，配置 MCP server 等于信任其代码（README 已提示）。
- pgvector 表结构与维度绑定，切换 embedding 维度需要重建表；没有做自动迁移。
- pgvector 的集成测试依赖真实数据库，当前只做 SQL/字面量级单测 + 文档化手工验证，避免 CI 依赖容器。

## 何时重新考虑

- 需要 MCP 的 resources/prompts、HTTP/SSE 传输或重连策略时；
- 语料与并发继续增长，需要 HNSW/IVF 索引参数调优或独立向量数据库时。
