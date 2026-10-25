# 从源码构建

使用 JDK 21。源码包含 Gradle Wrapper，通过公开 Maven 仓库解析依赖，并按锁定哈希检查内容。

```powershell
./gradlew.bat build
```

Linux／macOS 使用 `./gradlew build`。产物位于 build/libs；生产 Jar 不包含测试模组或第三方依赖 Jar。

默认 core 配置不要求安装 Create。需要编译可选集成时使用 `-PcompatProfile=full`；隔离测试模组使用 `compileIntegrationJava`。构建包含回归与依赖检查。

PowerShell 7 下运行 `./scripts/Package-Release.ps1 -OutputPath '<新建的空目录>'` 可生成发布文件；`./scripts/Verify-Release.ps1 -ReleasePath '<发布目录>' -ExerciseInstaller` 检查包结构、校验和及隔离安装。打包范围由 scripts/release-files.json 明确列出。

Install-Release.ps1 与 Migrate-Workspace.ps1 只在关闭目标游戏后使用。需要运行测试客户端时新建隔离实例，保留玩家世界、账号和手写脚本。
