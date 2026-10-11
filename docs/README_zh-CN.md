<div align="center">

# MINEZ Roughly Enough Items

面向 MINEZ 服务器维护的 [Roughly Enough Items](https://github.com/shedaniel/RoughlyEnoughItems) Fork。

<a href="../README.md">English</a> ｜
<a href="README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/shedaniel/RoughlyEnoughItems"><img src="https://img.shields.io/badge/upstream-Roughly%20Enough%20Items-1976d2" alt="upstream"></a>
<a href="#受支持的版本"><img src="https://img.shields.io/badge/Minecraft-26.3-4caf50" alt="Minecraft"></a>
<a href="../LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<br>

<a href="https://github.com/MINEZ/RoughlyEnoughItems">项目仓库</a> ｜
<a href="https://github.com/MINEZ/RoughlyEnoughItems/releases">版本发布</a> ｜
<a href="#相对上游的改动">改动一览</a>

</div>

> [!IMPORTANT]
> **本项目是非官方 Fork，与 shedaniel 及 Roughly Enough Items 项目没有关系，既未经其认可，也不由其提供支持。**
>
> 请勿把与本 Fork 有关的任何问题带到上游仓库或其社区渠道。请向 MINEZ 维护者反馈 Fork 相关问题；本仓库目前未启用 Issues。
>
> 要区分两者，请先用官方构建复现问题：
>
> - **去掉本 Fork 后依然出现**：上游的问题。照常向上游反馈，且不要提及本 Fork。
> - **去掉本 Fork 后不再出现**：我们的问题。请在这里反馈，且只在这里反馈。

## 关于

本仓库跟随上游的 `26.3` 分支，并加入 MINEZ 服务器所需的少量改动。MINEZ 服务器是运行 [CraftEngine](https://github.com/Xiao-MoMi/craft-engine) 的 Paper 服务器。下文未列出的部分与上游完全一致。

### 受支持的版本

与上游一致，支持 Fabric 与 NeoForge 上的 Minecraft 26.3。ReiSync 物品同步只接入了 Fabric；其余改动都位于通用代码中，对两种加载器都生效。

## 相对上游的改动

| 改动 | 类型 | 加载器 |
| --- | --- | --- |
| 接收 ReiSync 服务端插件推送的 CraftEngine 物品 | 新增 | Fabric |
| 右键打开物品用途 | 修复 | Fabric、NeoForge |
| CraftEngine 物品的配方与用途查询 | 修复 | Fabric、NeoForge |
| REI 重载后保留服务端配方 | 修复 | Fabric、NeoForge |
| “配方数据不完整”警告 | 移除 | Fabric、NeoForge |
| 合成过滤器按钮图标和子菜单裁剪 | 修复 | Fabric、NeoForge |

**ReiSync 物品同步。** CraftEngine 物品只存在于服务器上，客户端自己无从列出它们。ReiSync 服务端插件会在玩家进入服务器后不久，以及执行 `/ce reload` 之后，通过 `reisync:items` 频道推送全部 CraftEngine 物品；本 Fork 负责接收，并把它们加入物品列表。每个物品以 Paper 序列化后的 NBT 形式传来，再逐个组件重建：客户端无法识别的组件会被跳过，而不是丢掉整个物品，因此服务器与客户端的 Minecraft 版本略有不同时也能正常工作。不过客户端不做数据升级，底层物品在客户端上不存在的物品会被丢弃。条目每 tick 加入 200 个（可用 `-Dreisync.batch=<n>` 调整，最少 16 个），且不会重新过滤整个列表，以减少每 tick 的处理量；离开服务器时，这些条目会被移除。启动时加上 `-Dreisync.debug=true`，可在日志中查看解码失败的物品。

**完整批次同步。** 配合 ReiSync 1.7.1 或更新版本时，`reisync:items_v2` 频道会携带批次 ID 和从 0 开始的分片序号。新批次会丢弃上一轮未完成的分片，只有完整、连续的批次才能替换物品列表；格式错误的分片不会提交半份列表。旧的 `reisync:items` 频道仍用于兼容旧服务器，但无法识别中断的批次。需要同时升级两个项目，才能获得完整批次保证。

**右键查看用途。** 网易等客户端不会把鼠标右键的“释放”事件交给 REI，因此右键点击物品毫无反应，只能按 U 键打开用途。现在右键在按下时就会触发。右键从不用于拖动，因此不会有任何损失；左键仍在释放时触发，保留了按住拖出物品的操作。作弊模式下右键用于给予物品，因此仍与上游一样在释放时触发。

**CraftEngine 配方。** CraftEngine 物品的底层是原版物品，其真实身份以 `craftengine:id` 的形式存放在 `minecraft:custom_data` 中。上游在查询配方与用途时按底层物品比较，导致 CraftEngine 物品会混入其底层物品的全部原版配方，反之亦然。现在该 ID 也参与比较，查询无结果时上游所做的通配回退也会保留它。ID 使用 64 位哈希，避免 Java 的 32 位字符串哈希中已知的碰撞导致不同 CraftEngine 物品混用配方。精确比较保持不变。

**服务端配方重载。** 在更新事件结束后保存当前连接的原版配方书快照，并在 REI 重载后恢复其中的服务端配方，包括本地配方回退触发的重载。服务器删除或整份替换配方时同步更新快照，退出连接后清理。服务端与本地配方的编号彼此独立，因此按显示内容去重。ReiSync 只同步物品条目；自定义配方仍需服务器通过配方书发送。

**“配方数据不完整”警告。** MINEZ 服务器没有安装 REI，REI 无法从服务器获得完整的配方数据。当 Local Recipes Fallback（本地配方回退）设为 Never 时，上游每次进入服务器都会在左上角显示一条警告。这在 MINEZ 服务器上是预期情况，因此本 Fork 完全不再显示该警告。

**菜单绘制。** 合成过滤器按钮恢复显示工作台图标。图标在绘制时延迟创建，并先检查 Minecraft 是否已绑定物品组件，避免启动界面初始化 REI 时过早创建 `ItemStack`。子菜单新建裁剪栈时使用完整的 GUI 范围，文字和控件可以正常显示在父菜单之外。

## 构建

需要 JDK 25。

```bash
./gradlew :fabric:build
```

在 Windows PowerShell 中使用 `.\gradlew.bat :fabric:build`。产物位于 `fabric/build/libs/`，文件名包含版本号 `26.3.824.4`。运行 `./gradlew :runtime:test :default-plugin:test`，可执行配方重载、菜单裁剪和同步相关的回归测试。

## 版本号

版本号为 `<base_version>.<upstream_build>.<fork_revision>`，三项均取自 `gradle.properties`。当前版本为 `26.3.824.4`，基于上游 CF CI 构建 `824`，Fork 修订号为 `4`。本 Fork 的 `GITHUB_RUN_NUMBER` 不再影响版本号，本地和 CI 构建都会保留额外修订号。更新上游基线或发布新版本时，请修改相应属性；临时构建也可用 `-Pupstream_build=824 -Pfork_revision=4` 覆盖。

上游项目中的下载链接指向官方构建，**不**包含这些改动。

## 上游

Roughly Enough Items 由 [shedaniel](https://github.com/shedaniel) 及其贡献者开发。如需了解上游发布的模组本身，[CurseForge 页面](https://www.curseforge.com/minecraft/mc-mods/roughly-enough-items)仍是合适的去处，但不适用于本 Fork 的任何内容。希望依赖 REI 的模组开发者，请使用[上游 README](https://github.com/shedaniel/RoughlyEnoughItems#maven) 中介绍的官方 Maven 构件；本 Fork 不会发布到任何 Maven 仓库。

这里的改动是为一个服务器的需要而做的，单独维护。请不要在上游的渠道中讨论它们。

## 许可证

Roughly Enough Items 以 MIT 许可证开源，Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel。本 Fork 中的修改以相同条款提供。完整文本见 [LICENSE](../LICENSE)。
