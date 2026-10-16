# marketing-engine

A Java and Spring Boot marketing and raffle system project organized with DDD-style layers.

## Project Structure

- `marketing-engine-api`: public service interfaces and DTOs
- `marketing-engine-app`: Spring Boot application, configuration, resources, and tests
- `marketing-engine-domain`: domain models and domain services
- `marketing-engine-trigger`: HTTP controllers, scheduled jobs, and message listeners
- `marketing-engine-infrastructure`: persistence, MyBatis mappers, Redis, and messaging adapters
- `marketing-engine-types`: shared exceptions, events, enums, and constants

The main Java package is `com.sjs.marketing`.

## Build

```bash
mvn clean test
```

The project targets Java 8. The local build environment should use a compatible JDK and Maven installation.

## Reference

本项目参考小傅哥《大营销》教程完成，代码和工程结构用于个人学习与作品集整理。

参考资料：[bugstack.cn](https://bugstack.cn/)

## License

This repository is maintained as a personal learning and portfolio project.