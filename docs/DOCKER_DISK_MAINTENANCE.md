# Docker磁盘清理与恢复

日期：2026-10-28。Docker虚拟磁盘约15GB，清理前可用0，占用100%；清理后约2.9GB可用，占用80%。宿主机本身有约90GB可用，问题来自Docker内部容量。

## 实际操作

- 清理超过1小时未使用的可重建构建缓存；删除镜像后再次清理失去引用的旧缓存。没有执行全局system prune或volume prune。
- 删除7个经过容器引用检查、无容器使用的通用镜像：tomcat:10.1-jdk17-temurin、maven:3.9.9-eclipse-temurin-17、nacos/nacos-server:latest、nginx:1.29-alpine、nginx:latest、python:3.9-slim、redis:latest。后续需要时可从原仓库重新拉取。
- 保留所有数据库卷，包括没有挂载的卷；保留项目自建镜像及其他项目的运行/停止容器。没有停止nonfood-oilplants数据库或改变Docker全局配置。
- compose.yaml为db/api/web补充日志轮转：10m × 3。只重建本项目api/web并验证轮转生效；数据库服务未重启，其日志限制将在后续正常重建时应用。

## 验证

- API从unhealthy恢复healthy；本项目MySQL保持healthy；Web运行。
- `scripts/security/check-local-runtime.py`生产只读回归通过，容器JAR与本地构建哈希一致。
- 当前公开目录60028、候选95238、正式事实0、科学审核决定0、生产用户/管理员0。没有发布或修改科学数据。
- 292份保护文件重新SHA-256校验292/292匹配。
- 部署Compose结构测试4/4通过。
- 当前镜像总计约5.476GB，构建缓存117.3MB；空间恢复以df实测为准，不能简单叠加镜像和共享缓存的显示大小。

## 仍需注意

约2.9GB可用足以解除此次写盘失败，但低于部署就绪脚本默认5GB余量门槛；保留该门槛，不能为了显示通过而降低。公网部署前应由环境所有者安排足够的Docker虚拟磁盘容量及备份空间。扩容或重启Docker可能影响其他项目，本次没有自动执行。

此次恢复不代表最终MySQL隔离全量测试与公网部署验收已经完成。原始操作日志在本地忽略目录reports/docker-disk/；本轮代码/文档变动未自动提交或推送。
