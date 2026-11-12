# 前后端分离改造说明

本文记录 zzbbs 从 "Spring Boot + Freemarker 服务端渲染" 改造成 "Java 只提供 JSON 接口 + 两个独立 React 前端" 的设计、URL 对照和部署方式，供以后修改时查阅。

## 1. 仓库分工

| 仓库 | 内容 | 挂载路径 |
|---|---|---|
| `co2ffee/zzbbs`（本仓库） | Java 后端，只提供 JSON 接口、验证码、第三方登录回调、WebSocket | 后端端口 8089 |
| `co2ffee/zzbbs-forum-web` | 用户端前端（React + Vite + TypeScript + MUI） | `/` |
| `co2ffee/zzbbs-admin-web` | 管理端前端（React + Vite + TypeScript + MUI + DataGrid） | `/admin-ui/` |

两个前端都是纯静态产物，由 nginx 托管，服务器（1.7G 内存）上不跑 Node 进程。各自的 GitHub Actions 把 `dist/` 打包成固定文件名发到固定 tag `deploy-latest` 的 release：

- 用户端：`forum-ui.tar.gz`
- 管理端：`admin-ui.tar.gz`（构建时 `--base=/admin-ui/`）

服务器用 mihomo 代理（`curl --socks5 127.0.0.1:7891`）拉取，脚本见各前端仓库的 `deploy/` 目录。

## 2. 鉴权

- **用户端**：登录接口返回 `token`，前端存 `localStorage`，之后请求带 `token` header（`BaseApiController.getApiUser()` 读取，查 `user.token`）。token 永久有效，直到手动刷新（`GET /api/settings/refreshToken`）。
- **管理端**：`POST /admin/api/login`（表单参数 `username`/`password`/`code`）返回 `token`，之后同样带 `token` header。新增的 `TokenAuthFilter` + `StatelessToken` 让 Shiro 支持无状态 token；`admin_user` 表有 `token` 列（迁移 `V1.22`）。
- **用户端内容需登录后才能查看**：`LoginRequiredInterceptor` 拦截 `/api/**`，没有有效 `token` 请求头一律返回 `401 + {"code":401,"description":"请先登录"}`。不拦截的入口：`/api/login`、`/api/register`、`/api/sms_code`、`/api/mobile_login`、`/api/site`、`/api/active`、`/api/oauth/session`（以及 CORS 预检 `OPTIONS`）。开关是配置项 `site.login_required`（`application.yml`/环境变量，默认 `true`，设为 `false` 恢复游客可浏览）。`/static/**`（上传的文件）、`/common/captcha`、`/oauth/**`、`/websocket` 不在拦截范围。前端对应：除 `/login`、`/register`、`/active`、`/oauth-login`、`/logout`、`/changeLanguage` 外所有页面有路由守卫，未登录跳 `/login` 并把原地址放在路由 state 里，登录后回到原地址；任意接口返回 401 时清掉本地登录态；未登录时顶栏隐藏标签/积分榜/搜索。
- 验证码：`GET /common/captcha`，依赖后端 session cookie，所以前端必须和后端同域（nginx 反向代理）。
- `User.token`、`AdminUser.password`、`AdminUser.token` 都加了 `@JsonIgnore`，不会随接口泄露。

## 3. 用户端 URL 对照（和原来的页面链接保持一致）

用户端保持原有的页面链接，老链接（收藏夹、邮件里的链接）继续可用。

| 原页面 URL | 现在由谁处理 | 备注 |
|---|---|---|
| `/`、`/index`、`/index.html`，`?tab=all/good/newest/hot/noanswer&pageNo=` | 用户端 | `/index*` 跳转到 `/` |
| `/topic/{id}` | 用户端 | |
| `/topic/create?tag=xxx` | 用户端 | `tag` 预置标签 |
| `/topic/edit/{id}` | 用户端 | |
| `/topic/tag/{name}?pageNo=` | 用户端 | |
| `/comment/edit/{id}` | 用户端 | 独立评论编辑页 |
| `/tags?pageNo=` | 用户端 | |
| `/user/{username}` | 用户端 | 最近话题和评论 |
| `/user/{username}/topics`、`/comments`、`/collects` | 用户端 | `?pageNo=` |
| `/settings`、`/notifications`、`/top100` | 用户端 | |
| `/login`、`/register`、`/logout` | 用户端 | |
| `/search?keyword=&pageNo=` | 用户端 | |
| `/changeLanguage?lang=zh\|en` | 用户端 | 切换后回到上一页 |
| `/active?email=&code=` | 用户端 | 邮件里的激活链接，调用 `GET /api/active` |
| `/oauth/redirect/{type}`、`/oauth/{type}/callback` | **后端** | 授权回调在后端，前端发起时带 `?spa=1` |
| `/common/captcha`、`/common/show_img` | **后端** | |
| `/static/**` | **后端** | 本地上传的文件 |
| `/websocket` | **后端** | 需要 nginx 的 websocket 升级头 |

管理端原来是 `/adminlogin`、`/admin/index`、`/admin/topic/list` 等页面链接，但 `/admin/**` 这些路径现在是 JSON 接口，不能同时当页面用，所以管理端 SPA 放在 `/admin-ui/`，nginx 对老页面地址做 301：

- `/adminlogin` → `/admin-ui/login`
- `/admin`、`/admin/index` → `/admin-ui/`

## 4. 后端接口清单

### 用户端用到的（`/api/**`，均返回 `{code, description, detail}`，`code=200` 成功）

已有：`GET /api/`（首页）、`/api/tag/{name}`、`/api/tags`、`POST /api/login`、`/api/register`、`/api/sms_code`、`/api/mobile_login`、`POST /api/upload`、`/api/topic`（详情/创建/修改/删除/点赞）、`/api/comment`（创建/修改/删除/点赞）、`/api/collect/{topicId}`、`/api/user/{username}[/topics|/comments|/collects]`、`/api/notification/{notRead|markRead|list}`、`/api/settings`（修改资料、邮箱、密码、token）。

为前后端分离新增：

| 接口 | 作用 | 替代了原来的 |
|---|---|---|
| `GET /api/search?keyword=&pageNo=` | 搜索话题 | Freemarker `tag_search` |
| `GET /api/top100?limit=` | 积分榜（只返回 id/用户名/头像/积分） | `tag_score` |
| `GET /api/site` | 站点公开配置（名称、分页大小、上传限制、是否开启短信、第三方登录列表、websocket） | `site` 全局变量、`tag_social_list` |
| `GET /api/settings` | 当前登录用户的最新信息 | 设置页的服务端渲染 |
| `GET /api/oauth/session` | 第三方登录回调后，前端用 session 换 token（token 不进 URL） | 回调里写 cookie |
| `GET /api/active?email=&code=` | 邮箱激活 | `GET /active` |
| `GET /api/checkin/status` | 签到状态：是否开启、今天是否已签到、今天获得的积分、连续天数、积分范围 | —（新功能） |
| `POST /api/checkin` | 每日签到：在后台设置的范围内随机增加积分，每人每天一次（`checkin` 表 `(user_id, checkin_date)` 唯一索引兜底并发） | —（新功能） |
| `GET /api/exchange/products` | 积分兑换：上架的商品（名称、图片、简介、所需积分、库存）和当前用户积分 | —（新功能） |
| `POST /api/exchange/{id}` | 兑换商品（body 可带 `contact` 联系方式/收货信息）：原子地扣积分、减库存、生成兑换记录 | —（新功能） |
| `GET /api/exchange/orders` | 我的兑换记录（状态：0 待处理、1 已处理、2 已取消） | —（新功能） |
| `GET /api/comment/{id}` | 取单条评论（编辑页用） | `/comment/edit/{id}` 页面 |
| 话题详情 `GET /api/topic/{id}` 增加 `otherTopics` | 作者的其他话题 | `tag_other_topic` |

### 管理端用到的（`/admin/**`，需要 token header）

登录 `POST /admin/api/login`；其余沿用原控制器的路径，只是把返回视图的方法改成返回 JSON：`/admin/index`（仪表盘）、`/admin/{topic,comment,tag,user,sensitive_word,file}/list`、`/admin/system/edit`、`/admin/{admin_user,role,permission}/list` 及各自的 add/edit/delete 等。POST 参数是表单格式，只有 `/admin/system/edit` 是 JSON 数组。

## 5. 第三方登录（OAuth）流程

1. 前端跳转 `/oauth/redirect/{type}?spa=1`，后端在 session 里记 `_oauth_spa`。
2. 授权完成后回调 `/oauth/{type}/callback`（授权平台上配置的回调地址不变），后端把用户写进 session。
3. 因为带了 `_oauth_spa`，后端重定向到前端 `/oauth-login`。
4. 前端调 `GET /api/oauth/session` 取 token 并完成登录。

## 6. nginx 与上线顺序

nginx 示例见 `zzbbs-forum-web/deploy/nginx-forum.conf.example`。要点：

- `/` 指向用户端静态目录，`try_files $uri $uri/ /index.html`。
- 转给后端的路径：`api|common|static|oauth|admin|adminlogin|active|bot` 以及 `/websocket`（带 websocket 升级头）。注意 `active` 现在由用户端处理，**不要**再转给后端。
- 管理端 `location ^~ /admin-ui/`（用 `^~` 保证优先于正则）。
- 原有的 `location /api/upload` 必须改成 `location ^~ /api/upload`，否则会被转给后端的 `^/(api|...)` 正则抢走，大文件上传的 size/timeout 设置失效。
- `nginx -t` 通过后再 `nginx -s reload`。

建议上线顺序：先部署并验证管理端 → 再部署用户端 → 最后上线新版后端。新版后端上线后，旧的 Freemarker 页面会消失。

## 7. 后端清理状态（Freemarker 页面已删除）

Java 只保留接口，已完成：

- 删除 `controller/front` 下的页面控制器（`IndexController`、`TopicController`、`UserController`、`CommentController`、`TestController`）。
- 删除 `src/main/resources/templates/**`（用户端和管理端的 ftl）、`static/theme`、`static/admin`。
- 删除 `directive/**`（Freemarker 自定义标签）、`FreemarkerConfig`、`ShiroTag`、`util/BaseModel`、`util/LocaleMessageSourceUtil`、`interceptor/UserInterceptor`（原来用来保护页面路由）。
- 去掉 `pom.xml` 里的 `spring-boot-starter-freemarker`，去掉 `application.yml` 里的 `freemarker` 配置。
- `GlobalExceptionHandler` 所有错误都返回 JSON（不再渲染主题错误页）。
- `WebMvcConfig` 里显式注册了 `InternalResourceViewResolver`：删除 Freemarker 后没有任何 ViewResolver，controller 里 `return "redirect:/xxx"`（`/adminlogin`、`/oauth/**`、`/common/show_img`）会报 `Could not resolve view`。
- `/admin/**` 未登录时由 `ApiUserFilter` 直接返回 `401 + JSON`，不再 302 到登录页（302 的 Location 会带 jsessionid 和内网地址）。
- `application.yml` 设置了 `server.forward-headers-strategy: native`，在 nginx 后面重定向的 Location 才是 `https://域名`。
- `IndexAdminController`：删除 session 表单登录；`/adminlogin` 只做 302 跳到 `/admin-ui/login`。
- `ShiroConfig`：登录页/成功页/无权限页地址改为 `/admin-ui/...`。
- 保留：`CommonController`（验证码、`show_img`）、`OAuthController`（授权跳转与回调）、`BaseController.getUser()`、`/static/**` 资源映射（本地上传文件）。

注意：

- `TestController` 里曾经硬编码过一个火山引擎（volcengine）API key（已确认早已废弃），文件已删除，同时删掉了只被它用到的 `volcengine-java-sdk-ark-runtime` 依赖（版本是 `LATEST`）。
- 数据库里的 `theme` 系统配置项还在，但已经没有作用。
- `plugins/theme-simple-plugin` 是 Freemarker 主题插件，页面模板删除后不再适用，仓库里保留未动。

## 8. 测试与已知限制

- 后端有 H2 内存库 + MockMvc 的集成测试（`src/test/java/co/yiiu/pybbs/it/`），CI 里 `mvn test -DskipTests=false` 运行，不连任何线上服务。测试用的 MySQL 迁移转换见 `H2Migrations`。
- 系统已按本文档部署上线并做过端到端验证（用户端、管理端、权限、登录后可见）。
- 权限：代码里每个 `@RequiresPermissions` 的值必须存在于 `permission` 表（有测试覆盖）。`V1.23` 补上了原来缺失的 `role:add`、`admin_user:delete` 并授予超级管理员。新增需要权限的后台接口时，要同时加迁移。
- 管理端注意：后台列表接口里 `deleted` 等 `tinyint(1)` 字段经 JDBC 会变成 `true/false`，前端不要用 `=== 1` 判断。话题管理列表有意包含已删除话题（显示“已删除”，可恢复/彻底删除），仪表盘“今日新增”只统计 `deleted = 0`。
- 自带的 `plugins/theme-simple-plugin` 依赖模板主题，页面模板删除后不再适用。
- WebSocket 的 `bind` 消息由客户端上报 `userId`，服务端未校验（沿用原有设计）。
