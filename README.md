# AsyncTaskHub

基于 RocketMQ 的异步图片处理任务系统。用户提交图片处理请求后立即获得任务受理响应，实际处理（压缩 / 生成缩略图 / 加水印）交由消息队列异步执行，前端通过轮询获取处理进度与结果。

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [API 文档](#api-文档)
- [关键设计决策](#关键设计决策)
- [测试](#测试)
- [CI/CD](#cicd)
- [AI 辅助开发说明](#ai-辅助开发说明)
- [项目结构](#项目结构)
- [局限性与后续优化](#局限性与后续优化)

---

## 功能特性

- 图片上传后立即返回任务 ID，处理过程完全异步，不阻塞用户请求
- 支持压缩、缩略图生成、水印三种处理类型
- 完整的消息可靠性保障：失败重试、死信队列、消费幂等性、定时补偿
- 失败任务支持人工重触发
- 前端实时轮询任务状态，终态任务自动停止轮询
- Docker Compose 一键启动全部依赖（PostgreSQL、Redis、RocketMQ）

## 技术栈

**后端**
- Java 17 + Spring Boot 3.2
- PostgreSQL 16（MyBatis-Plus + Flyway）
- Redis 7（幂等性控制）
- RocketMQ 5.x
- Thumbnailator（图片处理）

**前端**
- React 18 + TypeScript + Vite
- Tailwind CSS

**工程化**
- pnpm workspace（monorepo）
- Docker / Docker Compose
- GitHub Actions（CI）
- JUnit 5 + Mockito + Testcontainers

## 系统架构

```
React 前端
    │ REST API
    ▼
Spring Boot 后端
    │
    ├─ TaskController / FileUploadController
    │
    ├─ TaskService ──────────────▶ PostgreSQL
    │
    └─ TaskProducer ─────────────▶ Redis（幂等标记）
            │
            ▼
     RocketMQ (image-process-topic)
            │
            ▼
     TaskConsumer（幂等判断 → 图片处理 → 状态更新）
            │ 重试耗尽
            ▼
     %DLQ% 死信队列 → TaskDeadLetterConsumer（标记 FAILED）

TaskCompensationJob（定时扫描长期 PENDING 任务，结合 Redis
幂等标记判断是否为真实卡住，非误判正常处理中的任务）
```

任务状态机：`PENDING → PROCESSING → SUCCESS / FAILED`，`FAILED`
状态可通过人工接口重新回到 `PENDING`。

## 快速开始

```bash
git clone https://github.com/feiesos/asynctaskhub.git
cd asynctaskhub
docker compose up --build
```

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |
| RocketMQ NameServer | localhost:9876 |

本地开发（不使用 Docker）：

```bash
# 后端
cd apps/server && ./mvnw spring-boot:run

# 前端
pnpm --filter web dev
```

## API 文档

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/files/upload` | 上传图片文件，返回 `filePath` |
| `POST` | `/api/tasks` | 创建异步处理任务 |
| `GET` | `/api/tasks/{taskId}` | 查询单个任务状态 |
| `GET` | `/api/tasks?status=&page=&pageSize=` | 分页查询任务列表 |
| `POST` | `/api/tasks/{taskId}/retry` | 重新触发失败任务 |

统一响应格式：

```json
{ "code": 0, "message": "ok", "data": { ... } }
```

## 关键设计决策

以下记录几个开发过程中经过实际权衡、值得说明依据的设计点。

**PostgreSQL 而非 MySQL**：任务的可变参数（压缩质量、水印文字等）使用 `JSONB` 存储，状态字段使用原生 `ENUM` 类型，比固定字段的MySQL 方案更贴合业务的灵活性需求。

**单体架构而非微服务**：两天的开发周期下，微服务的服务拆分、服务间通信、注册中心等基础设施成本会显著挤压核心业务逻辑的开发时间。当前架构通过生产者 / 消费者实现了业务逻辑的异步解耦，已经具备微服务思维中"故障隔离、异步通信"的核心特性；Consumer 逻辑已与 Controller物理解耦，未来可平滑拆分为独立服务。

**`retry_count` 与 `compensation_count` 分离**：前者是 RocketMQ消息层面同一条消息被重新消费的次数（直接读取消息自带的`reconsumeTimes` 属性），后者是补偿任务重新投递消息的次数。两者对应的失败场景完全不同——一个是"消息到达但处理失败"，一个是"消息可能压根没被消费"——混用同一字段会导致后续无法区分任务失败的真实原因，因此拆成两个独立字段。

**幂等性判断双重校验**：消费者在处理前用 Redis `SETNX` 建立处理标记防止并发重复消费，但幂等判断不完全依赖 Redis——若数据库中任务
已是终态（`SUCCESS`/`FAILED`），即使 Redis 标记因过期或故障而缺失，仍会被数据库状态判断短路拦截。单一机制存在失效窗口，双重校验是为了消除这个盲区。

**补偿机制复用幂等性设计判断"真实卡住"**：定时任务扫描超过 2 分钟仍处于 `PENDING` 的任务时，需要区分"消息发送失败真的卡住"和"刚好还未被消费但即将处理"两种情况——通过检查幂等性设计中已有的 Redis处理标记即可完成判断，无需引入额外机制，两处设计形成呼应。补偿次数设上限 3 次，超过后转 `FAILED` 并等待人工介入，避免异常任务无限期占用资源。

**文件上传与任务创建接口分离**：这是开发过程中发现的一处设计缺口——后端接口最初只设计了接收 `filePath` 字符串，未覆盖文件从浏览器到服务端的传输环节。补救时选择新增独立的文件上传接口而非改造已有的任务创建接口，因为改动范围更小，且两者本身是职责不同的操作。

## 测试

- Mapper 层使用 **Testcontainers** 启动真实 PostgreSQL 实例做集成测试，而非用 H2 替代——因为 `JSONB`、自定义 `ENUM` 等 PG 特有类型在 H2 下行为不一致，用 H2 测试通过不代表生产环境可靠
- Service / Consumer 层使用 **Mockito** mock 外部依赖，保证单元测试执行快、互相独立
- 测试补齐时不以覆盖率数字为目标，优先级为：状态流转正确性 > 异常与边界情况 > 并发场景 > 参数校验

```bash
cd apps/server && ./mvnw test
```

## CI/CD

`.github/workflows/ci.yml` 包含两个并行 Job：

- `backend-test`：执行 `mvn test`。Mapper 层测试已通过 Testcontainers 自行管理 PostgreSQL 容器生命周期，CI 未额外声明 `services` 字段重复起数据库，避免维护两套连接配置
- `frontend-build`：执行 `pnpm install --frozen-lockfile` + `pnpm build`，验证前端可正常编译

CI（保证代码质量）与 CD（镜像构建/推送）作为独立关注点分开设计。

## 项目结构

```
asynctaskhub/
├── apps/
│   ├── server/          # Spring Boot 后端
│   └── web/              # React 前端
├── scripts/
│   └── gen-jsonl.sh       # JSONL 记录生成脚本
├── docker-compose.yml
├── pnpm-workspace.yaml
└── AGENTS.md              # AI 智能体项目级上下文配置
```

## 局限性与后续优化

- 图片处理参数校验（极端尺寸、超大文件）尚未做精细化边界处理
- 补偿机制的扫描阈值（2 分钟）和重试次数（3 次）为演示场景下的固定配置，生产环境应结合实际处理耗时统计改为可配置项
- 死信任务当前依赖人工介入重触发，后续可考虑对特定失败类型（如临时性存储故障）提供自动化的延迟重试策略
