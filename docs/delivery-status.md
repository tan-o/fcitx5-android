# 本次交付：已改、未改与验证状态

按用户要求停止继续修改实现代码，提交当前已完成内容。

## 已合并的原修改包

基线为 `6998502528cd26efb6079556da92003638e4179c`，保留修改包截至 `80e73963bb4085e821cfb3309ef02d831b0506ef` 的提交历史：

- 剪贴板最近/已固定分栏，以及键盘内中文、拼音、首字母搜索。
- 语音按钮切换到用户选定的已启用输入法。
- 手写画布、候选注音、撤销和清空。
- 展开候选按首字部首/笔画筛选，保留原候选下标、输入代次与分页修复。

## 在修改包基础上已完成的调整

- 按“不要引入 Google 服务与监视”的要求，移除 ML Kit Digital Ink 依赖和模型下载入口。
- 恢复 Zinnia 本地识别引擎与 JNI，内置 6763 字的 Tegaki 简体中文模型；首次使用只复制本地资源。模型和引擎许可证随源码、APK 保留。
- 主应用清单显式移除 INTERNET、ACCESS_NETWORK_STATE，防止依赖合并带入这两项权限。
- 保留新版手写画布和候选交互；本地模型加载、识别放在后台协程，关闭与识别访问同步。
- 修复 Zinnia 对现代 C++ 的兼容性问题（make_pair、random_shuffle），补充 JNI 输入边界检查。
- 为剪贴板界面和搜索回调补充显式类型，修复 Kotlin 循环类型推断导致的编译错误。
- 修正上游旧主题测试：2.0 导入到当前 2.1 应当触发迁移；增加候选颜色与迁移后版本状态断言。主题生产代码未改。

## 未改／未做

- 未引入 Google 模型；最终采用 Zinnia/Tegaki，不是 ML Kit 或 Gboard 模型。
- 未继续调整语音切换、候选筛选和剪贴板搜索的业务行为（除上述编译修复）；保留修改包现有实现。
- 未修改原生 Fcitx/Rime 子模块版本、上游主题迁移实现、Android SDK/Gradle/Kotlin 版本配置。
- 未移除 AndroidX、Material 等 UI/基础库；它们不等同于 Google Play Services。
- 未实现应用内语音识别；语音功能仍为切换到其他输入法，其网络和隐私行为由目标输入法决定。
- 未构建 Release 或其他 ABI，未安装到手机，未做实机手写准确率、三星语音切换及候选快速切换测试。
- 未合并到默认分支，未发布 GitHub Release，未主动运行远程 Actions。
- 本机 SDK、Maven 镜像、代理、构建缓存和私有签名文件不纳入此提交。

## 已完成验证

- 构建命令：`:app:testDebugUnitTest :app:assembleDebug :plugin:rime:assembleDebug -PbuildABI=arm64-v8a`。
- 完整构建成功；8 项单元测试全部通过（剪贴板 3、字符串转义 2、主题序列化 3）。
- 主应用和 Rime APK 签名校验成功，证书 SHA-256 相同：`90ba9b481b495c868b526ae55fd359f50b0d97549491cbb02f1b7b5bb93145d9`。
- 两份 APK 均无 INTERNET/ACCESS_NETWORK_STATE 权限；DEX 中未发现 `com/google/android/gms`、`com/google/mlkit`、`com/google/firebase` SDK 类名。此为静态检查，不代表已经进行网络抓包或实机验证。
- APK 内置模型 SHA-256 与源码模型一致：`e16153d1ff267cd479aea260d6f71a3edda8b4ba06db2d121513adda65a4449e`。

## 本地 APK

APK 保存在仓库外的同级 `apks/` 目录，不提交到 Git。它们由本次提交前的相同实现代码构建，文件名中的 `80e73963` 是构建时 HEAD；本次提交随后记录未提交的实现修改和交付说明。

| 文件 | SHA-256 |
| --- | --- |
| org.fcitx.fcitx5.android-80e73963-arm64-v8a-debug.apk | 64784bc7907a1b3d98a2e54af0de5e6fd3eb843c2d374135cde45e43e6045232 |
| org.fcitx.fcitx5.android.plugin.rime-80e73963-arm64-v8a-debug.apk | 6d36e1795a9bc588de44f55d749d3131060905b0f3e735193197d6c86bce9edc |
