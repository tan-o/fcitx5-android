# 离线手写输入

使用包内 Zinnia 引擎和 Tegaki 简体中文模型，模型随 APK 打包。首次打开时只将资源复制到应用私有目录，不联网、不下载模型、不接入 Google Play Services、ML Kit 或 Firebase。

模型文件：handwriting-zh_CN.model
SHA-256：e16153d1ff267cd479aea260d6f71a3edda8b4ba06db2d121513adda65a4449e
下载来源：https://github.com/alexrao/zinnia-handwriting/blob/master/res/handwriting-zh_CN.model
原始项目和训练数据：https://github.com/tegaki/tegaki/tree/master/tegaki-models
模型许可证：MODEL-LICENSE.txt（LGPL 2.1）；引擎许可证：ZINNIA-LICENSE.txt（BSD）。

pinyin.txt 来自 mozillazg/pinyin-data 的通规字常用读音，用于候选注音，多音字不做上下文判断。

识别质量尚需真实笔迹验证，尤其是连笔、潦草笔迹和模型未收录的字。
