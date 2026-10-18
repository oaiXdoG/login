# 服务注册与发现

本文档是同一个应用下游戏服（logic）、登录服（login）、回调服（callback）以及管理后台之间注册、发现、上报的**总规则**。各项目的具体改动和验收项见各自仓库的 `docs/服务发现改造.md`：

| 项目 | 文档 |
| --- | --- |
| 游戏服 daoshinvgui | `daoshinvgui/docs/服务发现改造.md` |
| 登录服 danmu-login | `danmu-login/docs/服务发现改造.md` |
| 回调服 danmu-callback | `danmu-callback/docs/服务发现改造.md` |
| 管理后台 admin-background | `admin-background/docs/服务发现改造.md` |

## 1. 总体思路

- 只用 zfoo 自带的服务注册与发现，不再自己写 ZooKeeper 节点。
- **登录服、回调服是公共组件，做 zfoo provider；游戏服做 consumer**，启动后自动连上本应用的所有登录服、回调服，并主动上报自己的信息。
- 登录服、回调服是两个独立的提供方，各自有自己的协议模块、服务名和上报协议对象（第 3、4 节）。
- 登录服、回调服不关心游戏服在哪，只按收到的上报维护「区 → 游戏服」表。
- 管理后台是一个进程管理所有应用，游戏服用 HTTP 定时上报给后台。
- 应用之间用 ZooKeeper 路径隔离：A 产品的服务只能发现 A 产品的服务，互不影响。

```text
                 ZooKeeper  /danmu/<appKey>/provider
                  ▲ 注册 provider          ▲ 注册 provider
                  │                        │
    登录服（login-loginReport）  回调服（callback-callbackReport）
                  ▲                        ▲
       zfoo 连接 + LoginReport        zfoo 连接 + CallbackReport
                  │                        │  DanmuPush / DanmuGiftPush（回调服 → 游戏服）
                  └───── 游戏服（logic，consumer）─────┘
                                  │
                                  │ HTTP 定时上报（签名）
                                  ▼
                           管理后台（全局一个）
```

## 2. ZooKeeper 路径

- 所有服务的 `<net:registry>` 使用 `path="/danmu/${app.key}"`，以 App Key 结尾，不再带 `/zfoo`。
- zfoo 在该路径下自动维护 `provider`、`consumer` 两个子节点，业务代码不读写 ZooKeeper。
- zfoo 要求根节点 `/danmu/<appKey>` 的数据等于 `center`（`zookeeper`），否则启动时抛 `rootPath misconfigured`。旧版本在这个路径下写过 `logic/`、`login/`、`zfoo/` 节点，根节点数据不对，**切换前必须删除旧的 `/danmu/<appKey>`**（见第 9 节）。

## 3. 节点 ID、协议模块与服务名

zfoo provider 节点名的格式是 `<节点 ID> | <地址> | provider:[<协议模块>-<服务名>]`。登录服、回调服是**两个独立的提供方**，各自用自己的协议模块和服务名：

| 服务 | `<net:net id>` | 角色 | 节点名中的 `模块-服务名` | 示例节点 ID |
| --- | --- | --- | --- | --- |
| 登录服 | `${server.node-id}` | provider：`protocol-module="login"`、`provider="${server.service-name}"`，`server.service-name=loginReport` | `login-loginReport` | `login-1` |
| 回调服 | `${server.node-id}` | provider：`protocol-module="callback"`、`provider="${server.service-name}"`，`server.service-name=callbackReport` | `callback-callbackReport` | `callback-1` |
| 游戏服 | `${server.service-name}-${server.zone-id}` | 两个 consumer：`consumer="${login.service-name}"`（`loginReport`）、`consumer="${callback.service-name}"`（`callbackReport`） | — | `logic-1` |

zfoo 中两者的作用（4.1.4 源码）：

| 环节 | 依据 | 源码 |
| --- | --- | --- |
| 连接：consumer 连哪些 provider | 只比较**服务名**：游戏服的 consumer 值与对方 `provider` 逐字相同就连接 | `Register.providerHasConsumer` |
| 消息发给谁 | 按**协议模块**：消息类属于哪个模块，就只发给声明了该 `protocol-module` 的 provider | `Consumer.findProviders` |

规则：

- 游戏服的 `login.service-name`、`callback.service-name` 必须与登录服、回调服的 `server.service-name` 相同。
- 同一进程内 consumer 名不能重复（`ConfigManager` 启动校验），所以登录服、回调服的服务名必须不同。
- 服务名、协议模块名里不能有 `-` 和 `|`（zfoo 按 `-` 拆 `模块-服务名`，按 `|` 拆节点名）；节点 ID 里可以有 `-`。
- 同一服务部署多台时 `server.node-id` 各不相同（`login-1`、`login-2`）。
- provider 不配 `address`：zfoo 取本机地址，端口从 12400 起找可用端口，并自动启动 provider 的 TcpServer。
- consumer 的 `load-balancer` 是 zfoo 必填项，填 `random`；上报要发给每一台，不使用负载均衡（见第 5 节）。

## 4. 上报协议

登录服、回调服的上报协议是**两套独立的协议对象**，结构目前相同，各属于提供方自己的协议模块。模块号、模块名、协议号、类名、字段顺序和类型在提供方和游戏服两边完全一致（包名可以不同）：

协议号按**模块号 × 1000** 分段，上报协议排在每个模块最前面，业务协议在后：

| 模块（号） | 协议 | 协议号 | 方向 | 登记在 |
| --- | --- | --- | --- | --- |
| `callback`（4） | `CallbackReport` | 4000 | 游戏服 → 回调服 | 回调服、游戏服 |
| `callback`（4） | `CallbackReportAnswer` | 4001 | 回调服 → 游戏服 | 回调服、游戏服 |
| `callback`（4） | `DanmuPush` / `DanmuGiftPush` / `DanmuGiftReply` | 4002 / 4003 / 4004 | 回调服 ↔ 游戏服（弹幕转发） | 回调服、游戏服 |
| `callback`（4） | `DouyinCallbackRequest` | 4010 | 抖音 → 回调服（HTTP，只在回调服内部使用） | 回调服 |
| `login`（5） | `LoginReport` | 5000 | 游戏服 → 登录服 | 登录服、游戏服 |
| `login`（5） | `LoginReportAnswer` | 5001 | 登录服 → 游戏服 | 登录服、游戏服 |
| `login`（5） | `LoginRequest` / `VersionRequest` / `LoginResponse` / `VersionResponse` | 5002～5005 | 客户端 ↔ 登录服（HTTP） | 登录服 |

- `callback` 模块内：4000～4009 是回调服与游戏服之间的协议；4010 起留给外部平台推给回调服的 HTTP 回调请求（抖音为 `DouyinCallbackRequest`，以后接入其他平台按 4011、4012… 排），不与游戏服传输。
- 弹幕转发协议（`Danmu*`）是回调服提供的协议，属于 `callback` 模块；原来单独的 `danmu` 模块取消，并入 `callback`（模块号沿用原 `danmu` 的 4）。
- 登录服的 `login` 模块号由 3 改为 5（游戏服的 3 号是 `game`）；HTTP 协议走 JSON 路由，协议号不在网络上传输，改号不影响客户端。
- 游戏服的 `protocol.xml` 登记 `callback`（4，上报 + 弹幕转发）、`login`（5，上报）两个模块；模块名、模块号必须与提供方相同，zfoo 才能按模块找到对方。
- `game` 模块（3）是客户端协议，沿用原有编号，不在本次调整范围。

`LoginReport` / `CallbackReport` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `serverId` | int | 节点 ID（二层 key），游戏服为区 ID，来自 `server.zone-id` |
| `instanceId` | String | 进程实例 ID，游戏服启动时生成一次 UUID，进程存活期间不变 |
| `host` | String | 公网地址，客户端连接用，来自 `server.public-host` |
| `port` | int | 客户端连接端口，实际监听的端口 |
| `innerHost` | String | 内网地址，`server.inner-host`，为空时自动取本机网卡的内网 IPv4 |
| `gmPort` | int | GM 接口实际监听的端口，未开放时为 0 |

`LoginReportAnswer` / `CallbackReportAnswer` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accepted` | boolean | 是否接受 |
| `message` | String | 拒绝原因，接受时为空字符串 |

字段只增不改：新增字段追加在末尾，提供方和游戏服同时加；不改名、不改类型、不删除。两套协议各自演进，互不影响。

## 5. 上报规则（游戏服）

1. **就绪后才上报**：客户端端口、GM 接口都启动成功后，游戏服才算就绪；就绪前不发送任何上报。
2. **按模块找连接**：`NetContext.getConsumer().findProviders(LoginReport)` 得到所有登录服连接，`findProviders(CallbackReport)` 得到所有回调服连接（zfoo 按协议所属模块匹配，没有连接时抛 `RunException`，视为空）。上报要发给每一台，逐条 `asyncAsk`，不走负载均衡。
3. **连上立即上报**：就绪时向当前所有登录服、回调服各发一次；之后每有新连接建立（`ConsumerStartEvent`，zfoo 设置好对方注册信息后才发出）立即向这条连接发对应的上报。
4. **定时上报**：每 10 秒向所有登录服发 `LoginReport`、所有回调服发 `CallbackReport`。
5. 超时或失败只打印警告，等下一次定时上报。
6. **区 ID 冲突**：某个节点对本进程的**第一次**上报回复 `accepted=false` 时，打印错误并退出进程（启动失败）；已经被接受过之后再收到拒绝，只打印错误，不退出。
7. 管理后台的 HTTP 上报见第 7 节。

## 6. 接收规则（登录服、回调服各自实现，规则相同）

登录服收到 `LoginReport`、回调服收到 `CallbackReport` 后各自维护内存表。同一应用只会连到本应用的游戏服，表的 key 只需要区 ID：

```text
serverId（区 ID）→ { report, session, reportTime }
```

1. **覆盖**：表里没有该区、或已有记录的 `instanceId` 与本次相同时，直接用本次内容覆盖（包括 `session`），回复 `accepted=true`。
2. **冲突**：已有记录的 `instanceId` 不同，且旧记录仍在线（见第 3 条）时，回复 `accepted=false`、`message="区 ID 已被其他进程使用"`，不覆盖；旧记录已不在线时直接覆盖并接受（同一区重启的进程）。
3. **在线判断**：`session` 连接可用（`session.getChannel().isActive()`）且 `当前时间 - reportTime ≤ 30 秒`。
4. **不删除**：记录一直保留，只靠在线判断决定能不能用；断开连接不删记录。
5. 业务只读这张表，不访问 ZooKeeper。

## 7. 管理后台上报（HTTP）

管理后台全局一个进程，地址配置在游戏服 `admin.report-url`。

- 请求：`POST <admin.report-url>`，`Content-Type: application/json`，请求体：

  ```json
  { "serverId": 1, "instanceId": "…", "host": "111.230.143.165", "port": 9020, "innerHost": "10.0.0.8", "gmPort": 9021 }
  ```

- 请求头：

  | 请求头 | 值 |
  | --- | --- |
  | `X-Admin-Project` | 本服 `app.key` |
  | `X-Admin-Timestamp` | 当前毫秒时间戳 |
  | `X-Admin-Signature` | `HMAC-SHA256(gm.secret, 时间戳 + "\n" + SHA256(请求体))`，SHA256 和 HMAC 都输出小写十六进制 |

- 时机：就绪后立即上报一次，之后每 10 秒一次；失败只打印警告。
- `admin.report-url` 或 `gm.secret` 为空时不向后台上报。
- 后台按 `App Key → 区 ID` 两层 key 覆盖保存，`当前时间 - 上报时间 ≤ 30 秒` 视为在线；记录不删除。验签和存储规则见 admin-background `docs/服务发现改造.md`。

## 8. 端口规则（游戏服）

| 配置项 | 有值 | 为空 |
| --- | --- | --- |
| `server.port`（客户端连接端口） | 使用该端口，被占用时启动失败 | 从 9020 起向上找可用端口 |
| `server.gm-port`（GM 端口） | 使用该端口，被占用时启动失败 | 从 9021 起向上找可用端口 |

- 上报的一律是实际监听的端口。
- `gm.secret` 为空时不开放 GM 接口，`gmPort` 上报 0。
- 端口不固定时，安全组 / 防火墙要放行对应的端口范围。

## 9. 上线步骤

新旧版本不兼容（旧游戏服是 provider，新游戏服是 consumer），本应用的游戏服、登录服、回调服一起切换：

1. 停掉本应用的游戏服、登录服、回调服。
2. 删除 ZooKeeper 上旧的 `/danmu/<appKey>`：`deleteall /danmu/<appKey>`（zkCli）。
3. 启动登录服、回调服、游戏服（顺序不限：zfoo 自动重连，连上即上报）。
4. 管理后台可单独发布；后台新版本发布后，游戏服上报到达前「按区自动发现」数据源读不到区，10 秒内恢复。

## 10. 其他依赖（不变）

- 直播间 → 区号的映射：登录服在主播登录成功时写入 Redis `danmu:<appKey>:room:<直播间ID>`，值 `{"zoneId":1}`，12 小时过期；回调服读取它决定转发到哪个区。回调服和登录服连接同一个 Redis（同一个库号）。
- 登录票据中带 `zoneId`，游戏服校验与自己的 `server.zone-id` 一致。
