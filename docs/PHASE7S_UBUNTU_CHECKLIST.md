# Ubuntu 部署前检查清单

这些步骤供服务器管理员执行。本轮未连接未知 Ubuntu，不操作 UFW、证书、DNS、端口或其他项目。

1. 确认部署目录和 Compose project 属于 VIRIDEXA；检查现有容器名、端口和 172.30.0.0/24、172.31.0.0/24 子网冲突。不能为本项目删除其他容器、网络或卷。
2. 保留 292 份原始文件及其 SHA 清单，挂载只读科研目录。不要在该目录自动清理、生成输出或重建文件。
3. 从服务器 Secret 管理或受控私有环境注入 MySQL 四类身份、账户/审核服务身份、真实 Turnstile Site Key/Secret、精确域名和 `WEB_ORIGIN=https://...`；真实密码和 Key 不进入 Git、终端回显或截图。确认私有文件权限。
4. 审核镜像版本和来源；可选 cloudflared 2026.10.3 已核对官方发布，但本机 Docker Registry manifest 拉取超时，服务器必须先验证镜像可用性和实际 digest。需要完整依赖漏洞扫描时另行执行，不能将本轮应用测试替代 SCA。
5. 选择同源入口：Cloudflare CDN 与 Cloudflare Tunnel 的实际直接 peer 不同。CDN 候选配置由 `python3 scripts/security/update-cloudflare-cidrs.py` 获取官方范围，人工核对后通过独立 overlay 挂载；直接源站默认不信任任何转发头。
6. 如使用 Tunnel：设置服务器端 Tunnel Token；将 Cloudflare hostname 指向 `http://web:8080`；使用 `compose.yaml + compose.production.yaml + compose.tunnel.yaml`。Tunnel overlay 自动替换可信 include，只信任独立 edge 网络固定 connector 地址，不要复制 CDN 公网 CIDR 代替它。
7. 执行 `bash scripts/security/preflight.sh`，Tunnel 模式执行 `bash scripts/security/preflight.sh --tunnel`，然后对实际三文件组合执行 `docker compose ... config -q`。预检不打印 Secret，也不会修改防火墙。
8. 生产 overlay 默认不发布任何 DB/API/web 宿主端口。选择 CDN 直连时还需管理员明确建立 TLS 入口和仅允许合适源站访问的 ingress；不能把应用内部 HTTP 端口直接全网暴露。Tunnel 不需要为应用绑定公网端口。
9. 首次流量观测可设 `VIRIDEXA_NGINX_OBSERVE=on`，认证限流仍拦截；核对真实正常请求节奏后设 off。默认阈值不是生产容量基准，校园 NAT 等共享 IP 需要实际调参。
10. 只启动当前 VIRIDEXA 项目的 Compose 组合。模板或单文件 bind mount 更新后，应重新创建本项目 web 容器以重新渲染模板/绑定 inode，随后运行 `nginx -t`；不能仅改模板文件而假定运行配置已刷新。不要重启其他项目。
11. 从实际 Docker 网络检查 Nginx→API 的 peer 与 `TRUSTED_PROXY_CIDRS=172.30.0.10/32` 相符；验证 `CF-Connecting-IP` 只在官方 CDN peer 或固定 Tunnel peer 下生效。伪造 XFF、CF 头和私有头的源站直连请求不能取得任意访客身份。
12. 通过公开 HTTPS 域名验证 Secure/HttpOnly/SameSite Cookie、HSTS、同源 Host（含自定义端口时）、CSRF 旋转及并发请求；管理员登录与审核必须使用真实授权身份。不要为验收伪造科学审核人。
13. 核对只读 API 数据库账号不能写入；账户身份只能固定注册 MEMBER，审核身份不能拥有不必要的 DDL；导入与 Flyway 使用各自身份。管理员初始化仍为受控 CLI，没有匿名管理员创建 API。
14. 执行真实 Turnstile `register` / 渐进 `login` 正向、失效、重复、hostname/action 不匹配和超时案例。官方测试密钥不能证明真实域名校验；本地观测到 dummy 响应缺 action 时已严格拒绝。真实 Key 测试不得用自动化解答交互挑战。
15. 检查 API docs 在生产关闭；公网 Actuator 不可读，内部健康探针正常；原文、科研文件路径和审核备注不出现在公开 API。当前未实现密码找回、邮箱验证或上传，不能展示虚假成功入口。
16. 检查日志留存和访问权限，分享前脱敏。公开看板只展示安全配置及聚合结果；不要发布 IP、凭据、完整 Token、问题原文或私有来源内容。
17. 如配置真实模型，先设模型/Token/费用预算与供应商价格，再做受控成本联调；目前领域检索降级仍可运行。没有 Key 不能宣称真实 LLM 联调通过。
18. 备份本项目数据库、审核历史与 Agent quota 卷，核对磁盘与 cgroup 资源限制；单实例内存窗口不跨重启/副本共享。只对自有、授权环境进行有界测试，不做公网 DDoS 或兄长其他服务的压力测试。

本机复现脚本：`check-local-edge.py` 只访问本机固定端口和独立 mock upstream；`check-local-runtime.py` 只读当前本机 VIRIDEXA Compose 与科研哈希。它们不是公网压力测试工具。
