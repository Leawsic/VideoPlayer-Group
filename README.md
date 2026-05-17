# VideoPlayer 开发说明

本目录包含 VideoPlayer Fabric 模组源码：

- `main/`：通用和服务端逻辑。
- `client/`：客户端渲染、播放、命令、本地区域和群组观影逻辑。
- `group-cinema.md`：群组观影功能说明和使用流程。

## 群组观影快速入口

群组观影通过客户端 WebSocket 连接外部 Room Server，不依赖 Minecraft 服务端 packet。

基本流程：

```text
/vlc local area create cinema 0 60 0 10 70 10
/vlc local screen create cinema screen1 1 65 1 1 60 1 9 60 1 9 65 1
/vlc group connect ws://127.0.0.1:8080
/vlc group create 我的房间
/vlc group bind local:cinema screen1
/vlc group play <url>
```

更多说明见 [`group-cinema.md`](group-cinema.md)。
