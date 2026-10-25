# KubeJS Editor 0.18.0

Minecraft 1.21.1 / NeoForge 的游戏内 KubeJS 可视化编辑器，当前为公开测试版。

从 JEI 拖入材料和产物，新增、替换或禁用配方；注册物品、常见方块、作物与可配置存储；通过待办、代码预览、批量撤销和回滚管理修改。强制兼容提供自动适配、字段映射和原始 JSON 补丁。

## 安装

关闭游戏，将 `kubejs-editor-0.18.0.jar` 放入实例的 `mods` 目录，安装匹配的 KubeJS、Rhino 和 JEI。同一实例只保留一个编辑器版本。`-sources.jar` 用于查看源码，无需放入 `mods`。

也可在 PowerShell 7 中执行：

```powershell
./Install-Release.ps1 -InstancePath '<实际实例目录>'
```

安装器备份旧 Jar；遇到非空的旧编辑器工作区会停止迁移，保留原数据。

进入世界按 **K**，或执行 **/kubejseditor**（别名 **/kjseditor**）。具体操作见随包的 GUIDE.md；依赖版本和能力边界见 COMPATIBILITY.md。

## 强制兼容

默认开启，位于最后一栏。优先使用真实配方或数据包注册表来源，自动比较适配方式；未知字段可以确认映射或使用“更多 → 通用字段补丁”编辑。必须存在真实可写数据来源，单独改变 JEI 展示无法改变机器加工。原生格式校验、来源确认和加载检查仍然适用。

普通配方通常重载生效；内容注册、原生数据包注册表和部分配置需要重启。直接应用面向本机单人世界／集成服务端。

## 源码与许可

源码包包含构建代码、测试代码和必要构建文件。JDK 21 下运行 `./gradlew build`；详细说明在源码包的 docs/distribution/BUILD.md 中。

基于 [Astronomy20/KubeJS-Recipe-Editor](https://github.com/Astronomy20/KubeJS-Recipe-Editor)，保留 MIT 许可及署名，详见 LICENSE 和 NOTICE.md。

[项目主页](https://github.com/meizuishan/kubejs-editor) · [下载与反馈](https://github.com/meizuishan/kubejs-editor/releases/tag/v0.18.0)
