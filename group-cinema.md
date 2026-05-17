# VideoPlayer 群组观影

## 功能目标

群组观影功能允许多个客户端连接到外部 Room Server，并通过 WebSocket 同步房间、播放、暂停、跳转、队列和播放进度。每个客户端仍然使用本地的 `ClientVideoScreen` 播放视频，Room Server 只负责转发房间状态和控制消息。

## 为什么不依赖 Minecraft 服务端

群组功能不使用 Minecraft 服务端 packet，也不要求当前服务器安装 VideoPlayer 服务端逻辑。这样可以支持：

- 纯客户端本地区域和屏幕。
- 不同 Minecraft 服务器上的玩家加入同一个 Room Server 房间。
- 单人游戏中使用 `singleplayer` 作为服务器标识。
- Room Server 独立部署和调试。

原有的服务端区域、服务端屏幕和 Fabric 网络包逻辑仍然保留，群组功能不会替换它们。

## 本地区域配置

本地区域配置文件位于：

```text
.minecraft/config/videoplayer-local-areas.json
```

客户端启动时会读取该文件。如果文件不存在、内容损坏或读取失败，会创建一个默认空配置并保存，避免客户端崩溃。

本地区域运行时名称会带有 `local:` 前缀，例如配置中的区域名是 `cinema`，运行时 area 名称是：

```text
local:cinema
```

这样可以避免与 Minecraft 服务端下发的区域名称冲突。

## 如何创建本地屏幕

先创建本地区域：

```text
/vlc local area create cinema 0 60 0 10 70 10
```

再创建屏幕四个角点：

```text
/vlc local screen create cinema screen1 1 65 1 1 60 1 9 60 1 9 65 1
```

查看配置：

```text
/vlc local area list
/vlc local screen list cinema
```

删除配置：

```text
/vlc local screen remove cinema screen1
/vlc local area remove cinema
```

进入本地区域后，客户端会创建对应 `ClientVideoArea` 和 `ClientVideoScreen`；离开区域后会卸载并清理播放器。

## 如何连接 Room Server

连接外部 Room Server：

```text
/vlc group connect ws://127.0.0.1:8080
```

连接成功后客户端会发送 hello 消息，包含协议版本、玩家 UUID、玩家名、当前 Minecraft 服务器标识和模组版本。

断开连接：

```text
/vlc group disconnect
```

发送原始 JSON 调试：

```text
/vlc group raw {"type":"ping"}
```

断线、错误或主动断开时，客户端会清理当前群组房间状态，并停止绑定屏幕上的本地播放。

## 如何创建/加入房间

创建房间：

```text
/vlc group create 我的房间
```

列出房间：

```text
/vlc group list
```

加入房间：

```text
/vlc group join <roomId>
```

离开或解散：

```text
/vlc group leave
/vlc group disband
```

查看状态：

```text
/vlc group status
```

## 如何绑定屏幕

群组播放必须绑定到一个已经加载的客户端屏幕：

```text
/vlc group bind local:cinema screen1
```

服务端区域也可以直接使用原区域名：

```text
/vlc group bind serverArea screen1
```

取消绑定：

```text
/vlc group unbind
```

`local:` 前缀需要保留，因为 `GroupClient` 会直接用该运行时名称从 `VideoPlayerClient.areas` 查找区域。

## 如何播放视频

房主播放视频：

```text
/vlc group play <url>
```

客户端会使用现有 `VideoProviders` 解析视频源，立即在绑定屏幕播放，并通过 WebSocket 发送包含完整 `VideoInfo` 的 `play` 消息。其它成员收到后会在自己的绑定屏幕上播放。

播放控制：

```text
/vlc group stop
/vlc group pause
/vlc group resume
/vlc group seek <seconds>
```

队列命令：

```text
/vlc group queue add <url>
/vlc group queue list
/vlc group queue skip
/vlc group queue clear
```

队列状态以后端 `playlist_update` 为准，`queue add` 不会直接修改本地队列。

## 离开区域停止播放的行为

如果绑定的是本地区域，玩家离开该区域时：

- 本地区域会卸载。
- 绑定屏幕变为不可用。
- 客户端会停止本地播放。
- 群组状态会标记为 suspended。
- 不会向 Room Server 发送 pause 或 stop，因此不会影响其它成员。

重新进入区域后，如果房间状态中仍有当前视频，客户端会按最新同步状态或房间进度恢复播放，并显示“群组播放已恢复”。

## 同步机制

房主客户端作为播放时钟源：

- 每 1000ms 发送 `sync_state`。
- 消息包含 `progress`、`paused`、`rate` 和 `clientTime`。

成员客户端收到后：

- 如果自己是房主则忽略。
- 如果未绑定屏幕或绑定屏幕不可用则不处理。
- 如果本地没有播放，会尝试用 `GroupRoomState.currentVideo` 恢复播放。
- 如果正在播放，会比较房主进度和本地进度。

纠偏策略：

- `abs(delta) <= 50ms`：不处理。
- `50ms < abs(delta) <= 1000ms`：轻微调速。
- `1000ms < abs(delta) <= 5000ms`：明显调速。
- `abs(delta) > 5000ms`：直接 seek。

如果当前播放器不是 `VideoPlayer`，无法调速时会退化为 seek。

## 限制和已知问题

- Room Server 协议需要外部实现，客户端只负责收发 JSON。
- 自动下一首当前不由客户端主动触发，主要依赖 Room Server 或手动 `/vlc group queue skip`。
- `VideoInfo` 过期时客户端会尝试通过 `rawPath` 重新解析；如果失败会回退到原信息。
- 本地配置损坏会重置为空配置。
- 群组功能不会修改原有 Minecraft 服务端区域逻辑。
- 断线后会停止绑定屏幕播放，避免留下无人管理的本地播放。
