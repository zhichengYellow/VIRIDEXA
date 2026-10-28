# Phase 7 administrator initialization

Use this command once, from the repository root, after the API JAR and local database are ready:

```sh
python3 scripts/pipeline/bootstrap_admin.py --email '<你的真实邮箱>' --display-name '<你的真实姓名>'
```

The script prompts for the password without echoing it, checks that it contains 12–72 UTF-8 bytes, and passes it only over standard input to the BCrypt helper. Passwords are never accepted as command-line arguments or environment variables. Build the API first if the helper JAR is missing.

An ordinary rerun for the same administrator reports that initialization is complete without prompting or changing the password. A different administrator identity is refused. The insert path repeats the check while holding a MySQL named lock on the same connection, then inserts the account and ADMIN role in one transaction. SQL errors roll back, release the lock, and are re-raised to the client. This is a local, deliberate initialization command and must not be run by application startup, ingestion, or test workflows.


请在本机交互式终端执行，替换上述占位符。使用密码管理器生成独立随机密码（12–72 UTF-8字节），在隐藏提示中输入两次；不要将密码发到聊天、参数、Git或前端配置。脚本拒绝换行与控制字符。

成功后打开 http://localhost:5173/review，使用本人账户登录。生产账户目前未由Codex创建，因此生产登录验收仍待本人完成。SQL初始化仅通过本机受控Docker管理权限执行，应用API没有创建管理员的匿名入口；Web运行账户仍为只读，审核池仅获得业务所需列权限。

已在独立临时MySQL子库验证并发初始化、同身份幂等、不同身份拒绝、缺失角色回滚与临时过程清理，详见 `reports/phase7/bootstrap-mysql.json`。这些账号仅是销毁的技术夹具，不是真实生产管理员。

公网HTTPS部署时设置服务器 `SESSION_COOKIE_SECURE=true`；本机HTTP验收保持false。Session保持HttpOnly、SameSite=Strict与20分钟失效；登录轮换Session ID，变更操作保留CSRF。数据库故障日志只记录异常类别，避免SQL、参数和内部路径进入日志。
