# 服务器部署准备与上线门禁

更新：2026-10-28。本次提交包含免费模型Agent、搜索联想和部署就绪门禁。源码可以推送，**当前运行环境不能批准公开上线**：本地 Docker 虚拟磁盘15GB已用尽，数据库文件系统可用空间为0，API unhealthy；最新 MySQL/生产API全链路验收尚未通过。

## 上线前必须由服务器所有者落实

- 独立项目目录与独立Compose项目；检查磁盘、内存、CPU、端口和172.30/172.31子网冲突。默认磁盘门槛为5GB空闲，原始科研资料、媒体和备份需要额外空间；不能据此认定5GB足够全部资产。
- 选择真实HTTPS域名，以及直接入口、Cloudflare CDN或Tunnel其中一种拓扑。当前配置不会自动修改UFW、DNS、TLS或其他项目。生产overlay没有宿主端口，必须由运维接入受控HTTPS入口，或配置Tunnel的 `http://web:8080`。
- 配置不同的MySQL迁移、导入、只读API、账户和审核身份及随机密码。依照 [快速启动](QUICK_START.md) 和 [生产安全配置](PHASE7S_DEPLOYMENT.md) 完成初始化；已有数据库不要重新初始化或删除卷。
- 使用服务器私有0600环境文件或secret manager，不把真实配置提交Git。Turnstile必须配置真实site key、secret和精确hostname；生产强制secure session cookie、禁API文档、限制内部管理端点。
- 原始数据和媒体不在源码仓库。按 [数据来源](DATA_SOURCES.md) 准备 `data/`、许可可公开的 `data/media/public/` 与MySQL持久卷；无原始资料时允许空库/无图回退，但不能宣称包含完整数据。
- 科研资产292份哈希必须重新核实。生产备份需要独立介质和恢复演练，见 `scripts/security/database-backup.py` 与 `database-restore-test.py`。任何时候禁止 `down -v`、危险 `git clean` 或全局 `docker system prune`。

## 可重复执行的准备检查

在仓库根目录，使用Java21、Node22及Docker Compose v2。按快速启动配置私有环境后执行：

```bash
./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml test
./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml -DskipTests package
npm --prefix apps/web ci
npm --prefix apps/web test
npm --prefix apps/web run build
scripts/deploy/check-readiness.sh
```

最后一条只读检查Docker/配置与数据库所在文件系统，不自动清理资源。磁盘阻塞必须由资源所有者处理，再运行隔离MySQL/REST集成、生产只读回归及浏览器验收。不得跳过失败测试或将旧报告当作本轮结果。

生产环境变量加载到当前shell后：

```bash
scripts/security/preflight.sh
# 或选定Tunnel拓扑： scripts/security/preflight.sh --tunnel
# 仅在所有门禁通过且获得服务器授权后启动：
docker compose -f compose.yaml -f compose.production.yaml up -d --build
# Tunnel部署再追加 -f compose.tunnel.yaml
```

`preflight.sh` 检查真实配置是否具备部署边界，不能代替数据库连通性、真实Cloudflare凭据及公网验证；不要输出完整 `docker compose config` 或 `docker inspect` 环境变量到报告。

## OpenRouter 免费模型配置

默认 `AGENT_MODEL_ENABLED=false`，普通目录/图谱不依赖模型。启用时仅将 `OPENROUTER_API_KEY` 注入服务端进程环境；不要写入Vue、Git或公开脚本。默认 `OPENROUTER_MODEL=openrouter/free`。其他模型须为服务端白名单内`:free`模型且每次通过实时零价格检查。

- 每站每日30次、滚动每分钟5次、每用户每日10次模型请求；每问题最多3次模型请求/6次只读工具。
- 所有实例共享 `viridexa_agent_runtime` 持久额度账本。失败、超时也计数，不能通过重启/删除账本恢复额度。
- 即时关闭：`docker compose exec api touch /agent-runtime/OPENROUTER_DISABLED`。费用异常自动持久熔断；调查完成后由运维移除标记并重启，不提供公众重置接口。
- 模型响应不可确认免费则停止并降级，不启用供应商收费插件、充值或付费回退。
- Key曾在聊天中提供，正式部署前建议轮换；本轮真实联调仅临时注入隔离后端，未保存到源码。

## 本轮发布范围及验收边界

详见 [Agent验收](AGENT_FLAGSHIP_ACCEPTANCE.md)。公开搜索联想只返回真实公开目录名称，支持中文名/来源别名/学名前缀，最多8条，无模型调用、无全库COUNT。首页、画廊和快捷搜索使用同一组件，支持中文输入法、防抖、取消和键盘选择。

本次只准备代码与文档并推送GitHub，不部署真实Ubuntu服务器、不修改其网络或其他服务，也不移动已有 `v0.1.0` 标签。当前磁盘问题解决前，不承诺生产可用或全链路验收通过。

## 本次提交的最新检查快照

Spring 177/177、前端50/50、pipeline18/18、部署配置隔离测试4/4通过；Vue类型检查/生产构建通过。配置测试覆盖direct/Tunnel结构、付费模型拒绝、非origin URL拒绝，不发送真实供应商请求。修复base与production overlay重复挂载 `/tmp`；production使用Compose的 `!override`，要求支持该标签的新版Compose v2。脱敏结果见 [PREDEPLOY_VALIDATION.json](PREDEPLOY_VALIDATION.json)。运行就绪门禁目前按预期拒绝磁盘不足环境。
