# 离线手写输入

使用 ML Kit Digital Ink Recognition 19.0.0 的简体中文笔迹模型。首次使用由用户点击下载，之后在设备上识别。模型约 20 MB，不随 APK 打包。下载需要能连接 Google 模型服务；SDK 不提供字节进度，因此界面显示不定进度条，不虚构百分比。

保留笔画时间与连续轨迹，按实际笔迹包围盒平移，避免在画布上写偏导致识别坐标发生变化。
接入文档：https://developers.google.com/ml-kit/vision/digital-ink-recognition/android

pinyin.txt 来自 mozillazg/pinyin-data 的通规字常用读音，用于候选注音，多音字不做上下文判断。

识别质量尚需真实笔迹验证，尤其是连笔、潦草笔迹和模型未收录的字。
