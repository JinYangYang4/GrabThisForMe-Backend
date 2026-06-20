# GrabThisForMe 后端项目

## 项目简介

GrabThisForMe 后端是前端 Android 项目的配套 Spring Boot 服务，负责用户认证、帖子与评论回复、订单、商品、店铺、社交关系、聊天会话以及消息推送等能力。

当前后端目标：

- 为前端提供稳定的 REST API
- 提供登录鉴权与 Bearer Token 解析
- 承接帖子评论/回复等逐步细化的前后端联调需求
- 使用本地 H2 文件数据库便于原型开发和快速验证

## 项目位置

后端项目目录：

```text
D:\projects\GrabThisForMe\GrabThisForMe-Backend\GrabThisForMe
```

对应前端项目目录：

```text
D:\projects\GrabThisForMe\xin\GrabThisForMe
```

如果是通过 Codex 或受限工作区协作：

- 前端通常在当前工作区内
- 后端通常在工作区外
- 因此读取后端文件、执行 Maven Wrapper、查看后端 Git 状态时，常常需要额外权限

## 技术栈

- Java 21
- Spring Boot 3.5.0
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring WebSocket
- H2 Database
- Maven Wrapper

## 代码结构

主要包结构：

```text
src/main/java/com/study/grabthisforme/
├─ auth/         Token、认证拦截、密码处理、认证配置
├─ common/       通用响应、异常、基础工具
├─ config/       Web、WebSocket、CORS 等配置
├─ controller/   REST API 入口
├─ persistence/  JPA entity / repository
└─ service/      业务逻辑
```

当前主要控制器：

- `AuthController`
- `ConversationController`
- `GoodsController`
- `OrderController`
- `PostController`
- `PushController`
- `SocialController`
- `StoreController`
- `UserController`

## 认证与 Token

后端登录成功后返回 token，前端后续请求需要在请求头中携带：

```http
Authorization: Bearer <token>
```

当前 token 机制由 `auth/TokenService.java` 负责，特点：

- token 内含 `userId` 和过期时间
- 使用 `HmacSHA256` 签名
- 默认过期时间来自配置 `grabthisforme.auth.token-expire-hours`

如果请求缺少 token，接口会返回类似：

```json
{
  "code": 40101,
  "message": "Missing bearer token"
}
```

这不代表接口地址错了，而是说明当前接口需要认证。

## 配置说明

当前配置文件：

```text
src/main/resources/application.properties
```

核心配置：

- 应用名：`GrabThisForMe`
- 端口：`8080`
- 数据源：`jdbc:h2:file:./data/grabthisforme;MODE=MySQL;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`
- H2 Console：`/h2-console`
- JPA：`ddl-auto=update`
- token secret：`grabthisforme.auth.token-secret`
- token 过期小时数：`grabthisforme.auth.token-expire-hours=168`
- CORS：`grabthisforme.cors.allowed-origins=*`

## 本地运行

推荐在项目根目录执行：

```powershell
$env:MAVEN_USER_HOME='D:\projects\GrabThisForMe\GrabThisForMe-Backend\GrabThisForMe\.m2-local'
cmd /c mvnw.cmd spring-boot:run
```

启动后默认地址：

```text
http://localhost:8080
```

## 编译验证

推荐编译命令：

```powershell
$env:MAVEN_USER_HOME='D:\projects\GrabThisForMe\GrabThisForMe-Backend\GrabThisForMe\.m2-local'
cmd /c mvnw.cmd -q -DskipTests compile
```

这条命令已经验证通过。

如果是在受限环境下执行失败，优先排查：

1. 当前工作目录是不是后端项目根目录
2. 当前环境是否有权限访问工作区外的后端目录
3. 是否因为沙箱限制导致 Maven Wrapper 或缓存目录无法正常工作

不要在没有确认权限问题前，就直接判断是后端代码编译错误。

## 数据库说明

当前使用 H2 文件数据库，数据文件默认位于：

```text
data/
```

常见文件包括：

- `grabthisforme.mv.db`
- `grabthisforme.lock.db`

注意：

- 这些文件属于本地运行数据，不适合作为业务改动依据
- 如果只是改接口或编译验证，不要把数据库文件变更当成业务代码变更

## 与前端联调

当前前端默认连接本地后端：

```text
http://localhost:8080
```

联调时常见接口类别：

- `/api/auth/*`：登录、注册、认证相关
- `/api/posts/*`：帖子、评论、回复
- `/api/users/*`：用户信息
- `/api/stores/*`：店铺
- `/api/orders/*`：订单

近期帖子评论/回复链路已经向以下方向调整：

- 回复分页从 `offset` 改为 `beforeTime` 游标方式
- 评论和回复不再鼓励一次性返回完整大树结构
- 更倾向于拆分详情、评论分页、回复分页

后续如果继续调整帖子接口，应保持和前端分页方式一致。

## 新协作者注意事项

如果是新对话 AI 或新协作者接手，建议先知道这些：

1. 后端目录通常不在前端工作区内。
2. 编译失败时，先排查沙箱权限和工作目录。
3. 近期前后端联动重点在用户认证、帖子评论/回复、Repository 远程接入。
4. 不要把本地 H2 数据文件的变化直接当成需要提交的业务修改。

## 常用排查

### 1. 为什么浏览器访问接口返回 401？

多数情况是因为接口需要 Bearer Token，而不是地址本身错误。

### 2. 为什么访问 `/api/stores` 返回 `Missing bearer token`？

说明这个接口受鉴权保护，请带上：

```http
Authorization: Bearer <token>
```

### 3. 为什么编译命令在某些环境里跑不起来？

优先检查：

- 当前目录对不对
- 是否具备访问后端目录的权限
- 是否需要提权运行命令

## 后续建议

后续如果继续推进联调，建议补充：

- 更完整的接口清单
- DTO / View 返回结构说明
- WebSocket / 推送消息使用方式
- 认证接口示例请求与响应
