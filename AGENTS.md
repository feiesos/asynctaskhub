# AsyncTaskHub — Agent Guide

本文件为 AI 编程智能体提供项目级上下文，所有轮次的开发指令均基于此约束执行。

## 项目概览
AsyncTaskHub：基于 RocketMQ 的异步图片处理任务系统。
用户提交图片处理任务后立即返回受理结果，实际处理通过消息队列异步执行，
前端轮询任务状态。

## 技术栈
- 后端：Spring Boot 3.5.x + Java 21，Maven 构建
- 数据库：PostgreSQL 16（使用 Flyway 管理迁移，MyBatis-Plus 做数据访问，
  不使用 JPA/Hibernate）
- 消息队列：RocketMQ 5.x（rocketmq-spring-boot-starter）
- 缓存：Redis 7（用于消费幂等性控制）
- 前端：React 18 + TypeScript + Vite，pnpm 管理依赖
- 容器化：Docker Compose 编排全部服务

## 约束/范式

约束内容非必要不更新，否则向我 @feiesos 请求

### 仓库结构
仓库整体采用 pnpm 管理的 monorepo
apps/server   Spring Boot 后端（独立 Maven 项目）
apps/web      React 前端（pnpm workspace 成员）
scripts/      自动化脚本（如 JSONL 生成脚本）

### 后端分包规范
domain      实体类、枚举
mapper      MyBatis-Plus Mapper 接口
service     业务逻辑
controller  REST 接口层
mq          RocketMQ 生产者/消费者
config      配置类

### 代码风格约束
- 命名遵循阿里巴巴 Java 开发手册（驼峰命名，见名知意）
- 禁止在 Controller 层写业务逻辑，必须经过 Service/ServiceImpl
- 所有数据库变更必须通过 Flyway 迁移脚本，不允许手动改表结构
- 消费者逻辑必须考虑幂等性，禁止假设消息只会被消费一次
- 涉及关键技术选型（如缓存方案、锁机制）时，先给出 2 个以上可选方案
  及权衡说明，不要直接给出唯一实现

### 前端约束
- 使用函数组件 + Hooks，不使用 class 组件
- API 调用统一封装在 src/api 目录，组件内不直接写 axios 调用

### 测试约束
- 核心业务逻辑（Service 层、消息消费者的处理逻辑）必须编写单元测试，
  使用 JUnit 5 + Mockito，Mock 掉数据库和 RocketMQ 依赖
- 涉及数据库交互的逻辑（Mapper 层）使用 Testcontainers 起真实 PostgreSQL
  实例做集成测试，不使用 H2 等内存数据库替代
  （避免 PG 特有语法在 H2 下行为不一致导致测试失真）
- 消费者的幂等性逻辑必须有专门测试用例覆盖"同一消息重复消费"的场景，
  验证第二次消费不会重复执行业务逻辑
- 死信队列相关逻辑需要测试"重试耗尽后消息进入死信队列、状态被正确标记"的路径
- 每个功能轮次完成后，如涉及新增或修改业务逻辑，必须同步补充或更新对应测试，
  不允许将测试集中留到最后一轮统一补齐
- 测试类命名规范：XxxServiceTest / XxxMapperTest / XxxConsumerTest，
  与被测类一一对应，放在 test 目录下对应的包路径中
- 提交前必须确保新增代码不破坏已有测试（本地跑 mvn test 全绿），
  这一点也会由 GitHub Actions CI 中的测试任务强制校验

### Git 提交规范
- 每轮功能改动对应一次独立 commit
- commit message 遵循 Conventional Commits（feat/fix/refactor/test/chore）
- 每轮改动后都给出分支、提交以及 commit message 建议，由 @feiesos 审核并手动提交，禁止直接创建和提交任何分支

### 命令约束
- 尽量使用不影响全局环境（本机）的指令、命令或临时变量
- 如果要使用，请告知

## Proxy if you need
http(s)://127.0.0.1:7890

## Current Status

You can update this.

### Monorepo structure

- `apps/server/` — Spring Boot 3.5.16 + Java 21, Maven (`mvnw` wrapper)
- `apps/web/` — Vite 8 + React 19 + TypeScript 6
- Workspace root manages both via pnpm workspaces (includes `apps/server` for docker/build
  only; `apps/server/package.json` is minimal, actual build uses Maven)

### Commands

There are many scripts in package.json from every packages, like /package.json or apps/web/package.json, if you need you check or update.

### Backend architecture

Base package: `org.feiesos.asynctaskhub`

Layers:
- `controller/` — REST endpoints
- `service/` — business logic
- `mapper/` — MyBatis-Plus interfaces
- `entity/` — database POJOs
- `mq/` — RocketMQ producers/consumers
- `config/` — Spring configuration

Key dependencies:
- MyBatis-Plus 3.5.9 (`mybatis-plus-spring-boot3-starter`)
- RocketMQ 2.3.2 (`rocketmq-spring-boot-starter`)
- PostgreSQL driver (runtime scope)

### Configuration

All connection params use environment variable placeholders with local-dev defaults:

| Env var | Default | Used by |
|---------|---------|---------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | localhost / 5432 / asynctaskhub | PostgreSQL |
| `DB_USER` / `DB_PASS` | app / app123 | PostgreSQL |
| `REDIS_HOST` / `REDIS_PORT` | localhost / 6379 | Redis |
| `ROCKETMQ_NAMESRV` | localhost:9876 | RocketMQ |

Production defaults come from `docker-compose.yml` environment blocks.

### API proxy (dev vs container)

- In Docker Compose: nginx proxies `/api/` → `http://server:8080/`
- In local dev (pnpm dev): vite dev server runs on its own port; configure `VITE_API_BASE_URL` env
  to point to your local server (e.g. `http://localhost:8080`)
- Frontend axios instance at `src/api/index.ts` reads `VITE_API_BASE_URL` (fallback `/api`)

### Docker

- Server: multi-stage build (Maven → JRE), expose 8080
- Web: `pnpm install --frozen-lockfile` → build → nginx, expose 80
- RocketMQ NameServer on 9876, Broker on 10911
- postgres:16 with named volume `pgdata`

### CI / CD

`.github/workflows/ci.yml` and `cd.yml` exist but are empty — populate before merging.