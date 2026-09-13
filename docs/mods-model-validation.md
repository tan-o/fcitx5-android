# 模型与界面修改验证记录

2026-09-13

## 语音

15 秒录完再识别的实现已删除。豆包输入法没有供第三方免费调用的输入法接口；火山引擎提供的是收费云端语音 API。当前开源实时模型无法同时满足粤语、普通话、英文和预期识别质量，因此不在应用中保留半成品入口。

## 手写

旧 Zinnia/Tegaki 和 Google ML Kit 已移除。使用 RapidOCR 发布的 PP-OCRv6 small ONNX 权重，输入笔迹先按包围盒居中并渲染，再由 ONNX Runtime 本地推理。模型需用户首次点击后台下载，支持字节进度和续传。

尚无用户实际连笔轨迹可用于定量评测；不能宣称已验证该用户的连笔准确率。

## 布局与接口

- 翻译／查词面板挂在固定高度的键盘区域上方；查词列表在输入条上方展开。
- 空格移动光标时，键盘停止绘制按键并显示触控板；默认每 24 dp 移动一格，可设置 8–64 dp。当前为横向移动。
- DeepSeek 系统提示词可编辑，`{targetLanguage}` 替换成目标语言。
- 墨墨快捷查词只读调用官方 vocabulary、interpretations、phrases、notes 接口；结果在键盘上方向上悬浮显示，接口错误也只显示在浮窗中。
- 万象按方案覆盖六个 Octagram grammar 参数，空值不覆盖原方案。
- Actions 只构建 arm64；未运行 Android 模拟器。手机窗口、触控、麦克风和系统服务仍需真机确认。

来源：
- https://github.com/RapidAI/RapidOCR
- https://open.maimemo.com/api_bundle.yaml
