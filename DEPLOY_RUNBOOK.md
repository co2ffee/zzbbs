# 上线操作手册（给能 SSH 到服务器的人/AI）

目标：把拆出来的两个前端部署到阿里云服务器，再上线新版后端。背景和 URL/接口说明见 `FRONTEND_SPLIT.md`。

## 已知的服务器情况（来自 deploy.sh 和 nginx 配置）

- 应用目录 `/home/pybbs`，里面有 `deploy.sh`、`start.sh`、`shutdown.sh`、`pybbs.jar`。
- 后端端口 `8089`，健康检查 `curl http://127.0.0.1:8089/`。
- 服务器拉 GitHub release 走本机 mihomo 代理：`curl --socks5 127.0.0.1:7891 ...`。
- 内存只有约 1.7G，**不要在服务器上起 Node 进程或做前端构建**，只放静态文件。
- nginx 有多个 `server`；站点域名是 `dljdlj.top`（80 跳 443）。443 的 server（`server_name _`，约第 75–110 行）里有 `location /api/upload`（转给 `localhost:8089/api/upload`）和 `location /`（转给 `localhost:8089/`）。

## 总原则

1. 每一步改动前先备份，改完先验证再继续。
2. nginx 只用 `nginx -t` 通过后 `nginx -s reload`，**不要 restart**。
3. 严格按下面顺序：**管理端 → 用户端 → 后端**。后端新版上线后旧的 Java 页面就不存在了，所以前端必须先就位。
4. 任何一步失败，按该步的“回滚”处理，不要硬往下做。

## 0. 前置检查（不改任何东西）

1. 确认 GitHub 上两个前端仓库的 Actions 都成功、release 里有文件：
   - `https://github.com/co2ffee/zzbbs-admin-web/releases/tag/deploy-latest` 里有 `admin-ui.tar.gz`
   - `https://github.com/co2ffee/zzbbs-forum-web/releases/tag/deploy-latest` 里有 `forum-ui.tar.gz`
   - 没有的话到各仓库 Actions 页手动 Run workflow（可能没开启 Actions）。
2. 后端：PR `co2ffee/zzbbs#1`（分支 `claude/festive-maxwell-6p5o8x`）**要先合并到 `master`**。服务器的 `deploy.sh` 拉的是 master 构建出的 `deploy-latest/pybbs.jar`（`release-jar.yml` 只在 push 到 master 时触发）。合并前不要执行第 3 步。
3. 在服务器上查看现状并保存下来：
   ```bash
   cat /home/pybbs/deploy.sh
   sudo nginx -T 2>/dev/null > /root/nginx-before.txt
   sudo nginx -T 2>/dev/null | grep -n "configuration file"      # 找到 443 server 所在的配置文件
   ls -ld /home /home/pybbs
   ps -o user= -C nginx | sort -u                                 # nginx worker 用户
   ```
4. 备份：
   ```bash
   sudo cp -a /etc/nginx /etc/nginx.bak.$(date +%F-%H%M)
   cp -a /home/pybbs/pybbs.jar /home/pybbs/pybbs.jar.pre-split.$(date +%F)
   ```

## 1. 部署管理端（`/admin-ui/`）

1. 创建脚本 `/home/pybbs/deploy-admin-ui.sh`（内容见 `zzbbs-admin-web` 仓库的 `deploy/` 目录；没有的话按用户端的同名脚本改 URL 和目录）：
   - 下载 `https://github.com/co2ffee/zzbbs-admin-web/releases/download/deploy-latest/admin-ui.tar.gz`（`--socks5 127.0.0.1:7891`）
   - 校验压缩包、解压到 `/home/pybbs/admin-ui.new`，确认有 `index.html`
   - 原子替换 `/home/pybbs/admin-ui`，保留上一版 `admin-ui.bak`
2. 执行 `bash /home/pybbs/deploy-admin-ui.sh`，确认 `/home/pybbs/admin-ui/index.html` 和 `assets/` 存在。
3. 权限：nginx worker 用户必须能读 `/home/pybbs/admin-ui`。如果 `/home` 或 `/home/pybbs` 是 700，要么 `chmod o+x /home /home/pybbs`，要么把静态目录放到 `/usr/share/nginx/` 或 `/var/www/` 下（并同步改后面的 `alias`/`root`）。
4. nginx：在 443 的 `server { }` 里加（用 `^~` 前缀匹配，保证优先于任何正则；后面转给后端的正则 `admin(/|$)` 本来也不会匹配 `/admin-ui/`，加上更稳）：
   ```nginx
   location ^~ /admin-ui/ {
       alias /home/pybbs/admin-ui/;
       try_files $uri $uri/ /admin-ui/index.html;
   }
   ```
   `alias` 末尾的 `/` 不能少。
5. `sudo nginx -t && sudo nginx -s reload`。
6. 验证：`curl -sI https://dljdlj.top/admin-ui/` 返回 200；浏览器打开 `https://dljdlj.top/admin-ui/`，登录页能显示验证码，用真实后台账号登录。**此时后端还是旧版**，旧版没有 `POST /admin/api/login`（若上一次已部署带该接口的版本则可登录），登录不了属于预期，可跳过登录验证，等第 3 步后再验。
7. 回滚：`rm -rf /home/pybbs/admin-ui && mv /home/pybbs/admin-ui.bak /home/pybbs/admin-ui`；nginx 配置从 `/etc/nginx.bak.*` 恢复后 `nginx -t && nginx -s reload`。

## 2. 部署用户端（`/`）

> 这一步会让根路径 `/` 从“转给 Java”变成“指向静态文件”。动之前再确认第 1 步没有问题，且已做好 nginx 备份。

1. 创建 `/home/pybbs/deploy-forum-ui.sh`（内容见 `zzbbs-forum-web` 仓库 `deploy/deploy-forum-ui.sh`），执行后得到 `/home/pybbs/forum-ui/index.html`。同样检查目录权限。
2. 修改 443 `server { }` 中的 location，参考 `zzbbs-forum-web/deploy/nginx-forum.conf.example`：
   - 原来的 `location / { proxy_pass http://localhost:8089/; }` 替换成：
     ```nginx
     location / {
         root /home/pybbs/forum-ui;
         try_files $uri $uri/ /index.html;
     }
     ```
   - 新增一个转给后端的正则 location（注意 **不包含 `active`**，`/active` 邮箱激活链接由用户端处理）：
     ```nginx
     location ~ ^/(api|common|static|oauth|admin|adminlogin|bot)(/|$) {
         proxy_pass http://localhost:8089;
         proxy_set_header Host $host;
         proxy_set_header X-Real-IP $remote_addr;
         proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
         proxy_set_header X-Forwarded-Proto $scheme;
     }
     ```
   - **保留**原来的 `location /api/upload { ... }`（带上传大小/超时设置），但必须改成 `location ^~ /api/upload {`。nginx 的规则是：普通前缀匹配选出最长的之后，还会继续检查正则，**正则匹配上就用正则**，所以不加 `^~` 的话 `/api/upload` 会被上面的 `^/(api|...)` 正则抢走，大文件上传的设置就失效了。
   - 新增 WebSocket：
     ```nginx
     location /websocket {
         proxy_pass http://localhost:8089/websocket;
         proxy_http_version 1.1;
         proxy_set_header Upgrade $http_upgrade;
         proxy_set_header Connection "upgrade";
         proxy_set_header Host $host;
         proxy_read_timeout 3600s;
     }
     ```
   - 老后台页面地址跳到新管理端（浏览器直接访问才跳，SPA 的接口请求不受影响）：
     ```nginx
     location = /admin       { if ($http_accept ~* "text/html") { return 301 /admin-ui/; } proxy_pass http://localhost:8089; }
     location = /admin/index { if ($http_accept ~* "text/html") { return 301 /admin-ui/; } proxy_pass http://localhost:8089; }
     ```
3. `sudo nginx -t && sudo nginx -s reload`。
4. 验证（此时后端仍是旧版也能看到前端页面，但接口字段会对不上，**只检查页面能打开**）：
   - `curl -sI https://dljdlj.top/` 返回 200，内容是 React 的 `index.html`（里面有 `/assets/index-*.js`）。
   - `https://dljdlj.top/topic/1` 直接访问（刷新）不是 404，说明 `try_files` 生效。
5. 回滚：`mv forum-ui.bak forum-ui`，并把 nginx 里的 `location /` 恢复成转给 8089，`nginx -t && reload`。

## 3. 上线新版后端

前提：PR #1 已合并 master，GitHub Actions 里 `Build and release deploy jar` 成功，release `deploy-latest` 里的 `pybbs.jar` 是新的。

1. `cd /home/pybbs && bash deploy.sh`（脚本会备份旧 jar 为 `pybbs.jar.bak`，重启，并检查 8089）。
2. 注意：
   - 新版带 Flyway 迁移 `V1.22__add_admin_user_token.sql`（给 `admin_user` 加可空的 `token` 列），启动时自动执行。如果之前在开发环境连过生产库已经执行过，Flyway 会跳过。
   - 新版**不再渲染任何 HTML 页面**，`/` 上 8089 直接访问会是 404/JSON，这是预期。
3. 看日志确认启动正常（`log.file`，没有 Flyway/Shiro/Bean 创建报错）。
4. 回滚：`bash shutdown.sh && mv pybbs.jar pybbs.jar.new && mv pybbs.jar.bak pybbs.jar && bash start.sh`。**注意：回滚到旧 jar 后，旧 Java 页面会回来，但 nginx 的 `/` 已指向静态文件，需要把 nginx 的 `location /` 一并回滚。**

## 4. 全链路验证（后端和两个前端都就位之后）

用浏览器（或 curl）逐项检查：

1. `https://dljdlj.top/` 首页能列出话题；点进 `/topic/{id}` 能看到详情和评论。
2. `https://dljdlj.top/common/captcha` 返回验证码图片；`https://dljdlj.top/api/site` 返回 JSON（`code=200`）。
3. 用户登录/注册（带验证码），登录后右上角有头像；发话题、评论、点赞、收藏。
4. 搜索 `/search?keyword=xx`、积分榜 `/top100`、标签 `/tags` 和 `/topic/tag/{name}`、个人主页 `/user/{用户名}` 以及它的 `/topics`、`/comments`、`/collects`。
5. 设置页 `/settings`：改资料、上传头像、改密码。上传图片在发话题页测一次大图（确认 `/api/upload` 的大小限制仍有效）。
6. 通知：系统配置里 WebSocket 开着的话，另一个账号评论后，未读数应实时增加；没开则一分钟内轮询更新。浏览器开发者工具里 `/websocket` 应该是 101 Switching Protocols。
7. 第三方登录（如已配置 GitHub）：点登录按钮 → GitHub 授权 → 回到 `https://dljdlj.top/oauth-login` → 自动进入首页并已登录。授权平台上的回调地址仍是 `https://dljdlj.top/oauth/{type}/callback`，不用改。
8. 邮件里的激活链接 `https://dljdlj.top/active?email=...&code=...` 打开后跳到首页并提示激活成功。注意系统配置里的 `base_url` 要是 `https://dljdlj.top`。
9. 管理端：`https://dljdlj.top/admin-ui/` 登录后台，逐页检查：仪表盘、话题、评论、文件、标签、用户、敏感词、系统设置、后台用户、角色、权限，增删改至少各试一次。
10. 老链接：`https://dljdlj.top/adminlogin` 跳到 `/admin-ui/login`；`/admin/index` 用浏览器直接访问跳到 `/admin-ui/`。

## 5. 之后的日常更新

- 用户端/管理端：push 到各自仓库 `main` → Actions 打包 → 服务器执行对应的 `deploy-*-ui.sh`（可选：把这两个脚本的调用加进 `deploy.sh` 开头，用 `|| echo 警告` 防止前端更新失败中断后端部署）。
- 后端：push 到 `master` → Actions 打 jar → 服务器 `bash deploy.sh`。

## 6. 需要提前知道的限制

- 以上整套没有在带数据库的环境里做过端到端联调，第 4 节的验证请认真做；遇到字段对不上等问题，对照 `FRONTEND_SPLIT.md` 的接口清单排查。
- `plugins/theme-simple-plugin` 是 Freemarker 主题插件，已不再适用，不要部署。
- Docker 相关：`docker-compose.yml` 映射的是 `8080:8080`，而 `application.yml` 端口是 `8089`，如果用 Docker 部署需要自己核对。
