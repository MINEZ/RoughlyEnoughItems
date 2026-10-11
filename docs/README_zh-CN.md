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

<a href="https://github.com/MINEZ/RoughlyEnoughItems/issues">问题反馈</a> ｜
<a href="https://github.com/MINEZ/RoughlyEnoughItems/releases">版本发布</a> ｜
<a href="#相对上游的改动">改动一览</a>

</div>

> [!IMPORTANT]
> **本项目是非官方 Fork，与 shedaniel 及 Roughly Enough Items 项目没有关系，既未经其认可，也不由其提供支持。**
>
> 请勿把与本 Fork 有关的任何问题带到上游仓库或其社区渠道，请改用[本仓库的问题反馈](https://github.com/MINEZ/RoughlyEnoughItems/issues)。
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
| “配方数据不完整”警告 | 移除 | Fabric、NeoForge |
| 合成过滤器按钮图标和子菜单裁剪 | 修复 | Fabric、NeoForge |

**ReiSync 物品同步。** CraftEngine 物品只存在于服务器上，客户端自己无从列出它们。ReiSync 服务端插件会在玩家进入服务器后不久，以及执行 `/ce reload` 之后，通过 `reisync:items` 频道推送全部 CraftEngine 物品；本 Fork 负责接收，并把它们加入物品列表。每个物品以 Paper 序列化后的 NBT 形式传来，再逐个组件重建：客户端无法识别的组件会被跳过，而不是丢掉整个物品，因此服务器与客户端的 Minecraft 版本略有不同时也能正常工作。不过客户端不做数据升级，底层物品在客户端上不存在的物品会被丢弃。条目每 tick 加入 200 个（可用 `-Dreisync.batch=<n>` 调整，最少 16 个），且不会重新过滤整个列表，因此进入服务器时不会卡顿；离开服务器时，这些条目会被移除。启动时加上 `-Dreisync.debug=true`，可在日志中查看解码失败的物品。

**右键查看用途。** 网易等客户端不会把鼠标右键的“释放”事件交给 REI，因此右键点击物品毫无反应，只能按 U 键打开用途。现在右键在按下时就会触发。右键从不用于拖动，因此不会有任何损失；左键仍在释放时触发，保留了按住拖出物品的操作。作弊模式下右键用于给予物品，因此仍与上游一样在释放时触发。

**CraftEngine 配方。** CraftEngine 物品的底层是原版物品，其真实身份以 `craftengine:id` 的形式存放在 `minecraft:custom_data` 中。上游在查询配方与用途时按底层物品比较，导致 CraftEngine 物品会混入其底层物品的全部原版配方，反之亦然。现在该 ID 也参与比较，查询无结果时上游所做的通配回退也会保留它。精确比较保持不变。

**“配方数据不完整”警告。** MINEZ 服务器没有安装 REI，REI 无法从服务器获得完整的配方数据。当 Local Recipes Fallback（本地配方回退）设为 Never 时，上游每次进入服务器都会在左上角显示一条警告。这在 MINEZ 服务器上是预期情况，因此本 Fork 完全不再显示该警告。

## 构建

**菜单绘制。** 合成过滤器按钮恢复显示工作台图标。子菜单新建裁剪栈时使用完整的 GUI 范围，文字和控件可以正常显示在父菜单之外。

需要 JDK 25。

```bash
GITHUB_RUN_NUMBER=<上游构建号>.<Fork 修订号> ./gradlew :fabric:build
```

产物位于 `fabric/build/libs/`，例如 `RoughlyEnoughItems-26.3.<上游构建号>.<Fork 修订号>.jar`。在 Windows PowerShell 中，请先用 `$env:GITHUB_RUN_NUMBER = "<上游构建号>.<Fork 修订号>"` 设置该变量。

## 版本号

版本号为 `<base_version>.<构建号>`：`base_version` 取自 `gradle.properties`，构建号取自环境变量 `GITHUB_RUN_NUMBER`，未设置时为 `9999`。本 Fork 发布时，构建号由所基于的上游构建号加上 Fork 修订号组成：以 `26.3.824.1` 为例，`824` 是上游构建号，末尾的 `.1` 是 Fork 修订号。这样我们的版本号就不会与上游的发布版本冲突。

上游项目中的下载链接指向官方构建，**不**包含这些改动。

## 上游

Roughly Enough Items 由 [shedaniel](https://github.com/shedaniel) 及其贡献者开发。如需了解上游发布的模组本身，[CurseForge 页面](https://www.curseforge.com/minecraft/mc-mods/roughly-enough-items)仍是合适的去处，但不适用于本 Fork 的任何内容。希望依赖 REI 的模组开发者，请使用[上游 README](https://github.com/shedaniel/RoughlyEnoughItems#maven) 中介绍的官方 Maven 构件；本 Fork 不会发布到任何 Maven 仓库。

这里的改动是为一个服务器的需要而做的，单独维护。请不要在上游的渠道中讨论它们。

## 许可证

Roughly Enough Items 以 MIT 许可证开源，Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel。本 Fork 中的修改以相同条款提供。完整文本见 [LICENSE](../LICENSE)。
