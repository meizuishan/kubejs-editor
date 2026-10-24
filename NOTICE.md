# 来源与许可

本工程基于 [Astronomy20/KubeJS-Recipe-Editor](https://github.com/Astronomy20/KubeJS-Recipe-Editor)，起点提交 `d7848b095093df9c96d1c164b1df2cc3c0298d04`，保留其 MIT LICENSE 与版权声明。

复用的编译入口包括 Mod/Menu 注册、无背包槽的 RecipeBuilderMenu、配方 Codec 序列化辅助；JEI 虚拟槽处理按原实现改编。上游其余源码和模板保留供查阅，Gradle 明确排除其旧编辑/直接写文件界面，不装入交付 Jar。

[ViScriptRecipe](https://github.com/zhenshiz/ViScriptRecipe) 的三栏布局、槽位聚焦和序列组装工作流程作为设计参考。本工程独立编写画布和 KJS 保存流程，未复制该项目源码或资产，也不使用其 .recipe 加载器。

[KubeJS Studio](https://www.curseforge.com/minecraft/mc-mods/kubejs-studio) 的公开功能说明用于比较方块分类与属性覆盖范围。本工程依据当前 KubeJS 公开构建器 API 独立实现方块注册，不包含 Studio 的源码、界面代码或素材。其自定义许可不等同于本工程上游的 MIT 许可。

设备预览在运行时调用已安装 Create 的 JEI 动画和 Catnip 方块渲染接口；不复制或打包其贴图、模型和库。

Vintage 适配针对 BluePHS 的 Create: Vintage NeoForged，运行时调用其已安装的 JEI 动画；不打包 Vintage 源码、类或资产。原整合包的独立中文译文仅用于用户本机资源修复，不包含在本模组或源码交付包。

Minecraft、NeoForge、KubeJS、Rhino、JEI、Create 及其附属均为各自作者的作品。`libs/` 中仅为本机开发依赖，未打入本模组或发布包。本项目不是这些项目的官方产品。
