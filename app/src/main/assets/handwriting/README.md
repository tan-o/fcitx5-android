# 手写输入

使用 ML Kit Digital Ink 19.0.0 的 zh-Hans 模型。打开手写窗口后点击提示下载模型；需要能访问 Google 的网络，下载完成后在设备本地识别，不再需要联网识别。

不再使用 zinnia，也不需要在构建时下载 26 MB 模型。pinyin.txt 用于候选读音标注，来源为 mozillazg/pinyin-data 的通规字常用读音。多音字显示常用音，不是上下文注音。

官方集成文档：https://developers.google.com/ml-kit/vision/digital-ink-recognition/android
