# marketing-engine

一个面向营销活动的 Java 抽奖业务系统，采用 DDD 分层组织代码，围绕策略抽奖、活动参与、库存控制和奖励发放构建完整的业务闭环。

项目重点展示领域建模、规则编排、基础设施隔离和异步事件处理在实际业务中的组合方式。

## Features

- 策略抽奖：支持策略、奖品、权重规则和库存规则的组合。
- 规则编排：使用责任链和决策树组织抽奖校验与执行流程。
- 活动参与：处理活动账户、参与次数、额度和订单状态。
- 奖励发放：通过领域事件和消息监听解耦奖励处理流程。
- 返利处理：支持签到等行为触发的返利任务和异步结算。
- 基础设施隔离：通过仓储接口隔离领域逻辑与 MyBatis、Redis、消息组件。

## Architecture

```text
API / DTO
   |
Trigger  -->  Domain  <--  Infrastructure
   |             |             |
HTTP / MQ     Rules /         MyBatis / Redis /
Jobs          Activities      RabbitMQ / DB Router
```

## Modules

| Module | Responsibility |
| --- | --- |
| `marketing-engine-api` | Service interfaces and request/response DTOs |
| `marketing-engine-app` | Spring Boot entry point, configuration, resources, and tests |
| `marketing-engine-domain` | Entities, value objects, aggregates, domain services, and rules |
| `marketing-engine-trigger` | Controllers, scheduled jobs, and message consumers |
| `marketing-engine-infrastructure` | Repositories, DAO interfaces, MyBatis mappings, Redis, and events |
| `marketing-engine-types` | Shared events, exceptions, enums, and constants |

Java package: `com.sjs.marketing`

## Tech Stack

- Java 8
- Spring Boot 2.7
- Maven multi-module build
- MyBatis
- MySQL
- Redis / Redisson
- RabbitMQ
- Lombok

## Run Locally

Prerequisites:

- JDK 8
- Maven 3.8+
- MySQL, Redis, and RabbitMQ when running integration-dependent flows

Build and test:

```bash
mvn clean test
```

The active Spring profile and infrastructure connection settings are in `marketing-engine-app/src/main/resources/application*.yml`.

## Project Status

This is a personal portfolio project focused on demonstrating backend design and implementation structure. Some integration tests require locally available infrastructure services and matching configuration.

## License

For personal learning and portfolio use.