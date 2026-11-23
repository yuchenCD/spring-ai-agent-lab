# spring-ai-agent-lab

[![CI](https://github.com/yuchenCD/spring-ai-agent-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/yuchenCD/spring-ai-agent-lab/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**一个最小但完整的 AI Agent 实验场：Java 21 + Spring Boot 3 + DeepSeek（OpenAI 兼容协议）。**

不引入 Agent 框架，手写 ReAct 循环、工具注册、SSE 步进流式与对话记忆 —— 目的是把 LLM 应用的每一层都拆开看清楚，而不是再包一层魔法。

---

## 为什么做这个项目

- 大多数 Agent Demo 停在"能跑通"，缺少**可测试的边界**：本项目把 LLM 客户端、工具、循环、记忆都做成可替换的接口，测试用脚本化 LLM，不依赖网络与 API Key。
- 很多接入代码不看请求体：本项目直接用 JDK `HttpClient` 手写 OpenAI 兼容协议，**请求/响应格式在代码里一目了然**（见 [ADR-0001](docs/adr/0001-raw-http-client-over-framework.md)）。
- 想让 Java 后端工程师也能看清 Agent 的工程形状：工具即 Bean、循环即状态机、流式即 SSE、失败即结构化错误。

## 功能

| 能力 | 说明 |
| :--- | :--- |
| ReAct 循环 | 模型请求工具 → 执行 → 结果回填 → 继续，直到出最终答案或达到步数上限 |
| 工具注册 | 任何 `Tool` Bean 自动注册，JSON Schema 自动广播给模型 |
| 内置工具 | `calculator`（手写递归下降解析器，安全求值）、`clock`（IANA 时区） |
| SSE 步进流式 | 每个工具调用实时推送 `step` 事件，结束时推送 `done` |
| 对话记忆 | 可替换的 `ChatMemory` 接口，默认进程内、按会话截断 |
| 离线演示 | 没配 API Key 时自动切换 Mock 模型，克隆下来即可跑 |
| 统一错误格式 | 校验 / JSON / LLM / 未知异常全部映射为 `ApiError` |
| 测试 | 26 个测试，含本地 `HttpServer` 驱动的协议级测试，无外部依赖 |

## 架构

```text
User ──▶ Spring Boot API ──▶ Agent Loop ──┬──▶ Tool Registry ──▶ Tools (calculator / clock / yours)
                                          ├──▶ Chat Memory (in-memory, swappable)
                                          └──▶ LlmClient ──▶ DeepSeek / OpenAI-compatible API
```

```text
src/main/java/io/github/yuchencd/agentlab
├── agent/      AgentService（编排一轮对话）、AgentLoop（ReAct 循环）
├── llm/        LlmClient 接口、OpenAI 兼容实现、Mock 实现、消息与工具调用模型
├── memory/     ChatMemory 接口与进程内实现
├── tool/       Tool 接口、ToolRegistry、calculator 与 clock
├── web/        REST + SSE 控制器、请求/响应模型、统一异常处理
└── config/     AgentProperties（类型安全的配置绑定）
```

## 快速开始

要求：JDK 21、Maven 3.9+。可选：DeepSeek API Key（不配置则进入离线 Mock 模式）。

```bash
git clone https://github.com/yuchenCD/spring-ai-agent-lab.git
cd spring-ai-agent-lab

# 离线 Mock 模式：无需任何 Key 即可体验工具调用
mvn spring-boot:run

# 接入真实模型
export DEEPSEEK_API_KEY=sk-...
mvn spring-boot:run
```

> 兼容任何 OpenAI 协议的服务：改 `agent.llm.base-url` 与 `agent.llm.model` 即可（DeepSeek、OpenAI、通义、本地 vLLM 等）。

### 调用示例

同步对话（会返回本次所有工具调用）：

```bash
curl -s -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我算一下 (12+8)*3 是多少？"}'
```

```json
{
  "conversationId": "cfd263b6-02c2-4e42-a224-1d583fdb4899",
  "reply": "工具返回：60",
  "steps": [
    {
      "step": 1,
      "tool": "calculator",
      "arguments": "{\"expression\":\"(12+8)*3\"}",
      "result": "60"
    }
  ],
  "stepLimitReached": false,
  "provider": "mock"
}
```

步进流式（SSE）：

```bash
curl -N -X POST http://localhost:8080/api/v1/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"demo-1","message":"现在几点了？"}'
```

```text
event: step
data: {"step":1,"tool":"clock","arguments":"{}","result":"2026-11-23T16:30:00+08:00 SUNDAY"}

event: done
data: {"conversationId":"demo-1","reply":"现在是 ...","steps":[...],"provider":"deepseek"}
```

## API

| Method | Path | 说明 |
| :--- | :--- | :--- |
| `POST` | `/api/v1/chat` | 一轮对话（阻塞），返回回复与工具轨迹 |
| `POST` | `/api/v1/chat/stream` | 同上，但以 SSE 推送 `step` / `done` / `error` 事件 |
| `GET` | `/api/v1/tools` | 当前注册给模型的工具及其 JSON Schema |

错误统一为：

```json
{"status":400,"code":"validation_error","message":"message: message 不能为空","timestamp":"..."}
```

`code` 取值：`validation_error`、`invalid_json`、`bad_request`、`llm_error`、`internal_error`。

## 添加一个工具（3 步）

```java
@Component
public class WeatherTool implements Tool {

  @Override public String name() { return "weather"; }

  @Override public String description() { return "查询城市天气，参数 city；返回值仅用于演示。"; }

  @Override public Map<String, Object> parameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of("city", Map.of("type", "string", "description", "城市名")),
        "required", List.of("city"));
  }

  @Override public String execute(String argumentsJson) {
    // 解析参数、调用外部服务、返回纯文本
    return "晴，18℃";
  }
}
```

重启即可，`ToolRegistry` 会自动发现并广播给模型；无需改动循环或控制器。

## 配置

| Key | 默认值 | 说明 |
| :--- | :--- | :--- |
| `agent.llm.provider` | `deepseek` | `deepseek`/`openai` 等标签，或 `mock` 强制离线 |
| `agent.llm.base-url` | `https://api.deepseek.com` | OpenAI 兼容端点，末尾不要带 `/chat/completions` |
| `agent.llm.api-key` | `${DEEPSEEK_API_KEY:}` | 为空时自动使用 Mock 模型 |
| `agent.llm.model` | `deepseek-chat` | 模型名 |
| `agent.llm.timeout` | `30s` | 单次 LLM 调用超时 |
| `agent.loop.max-steps` | `6` | 单轮最多工具调用步数 |
| `agent.memory.max-messages` | `20` | 每个会话保留的历史消息数 |

## 测试

```bash
mvn test
```

覆盖：算术解析器（含除零/非法字符）、工具注册与路由、ReAct 循环（工具成功 / 工具失败转 ERROR / 步数上限）、
OpenAI 协议序列化与解析（本地 `HttpServer` 模拟，含非 2xx）、Mock 模型行为、Web 层校验与错误映射。

## Docker

```bash
docker compose up --build          # 离线 Mock 模式
DEEPSEEK_API_KEY=sk-... docker compose up --build
```

##  roadmap

- [ ] `v0.2` — 真实 token 级流式输出（解析 DeepSeek SSE）
- [ ] `v0.2` — `ChatMemory` 的 Redis 实现与会话 TTL
- [ ] `v0.3` — RAG 工具：文档切片 + embedding + pgvector 检索
- [ ] `v0.3` — MCP 客户端：把外部 MCP server 的工具注册进 `ToolRegistry`
- [ ] `v0.4` — 评测集：同一问题多模型/多 prompt 的对比与回归

## License

[MIT](LICENSE)
