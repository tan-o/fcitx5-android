# 离线手写输入

使用 RapidOCR 发布的 PP-OCRv6 small 中文识别模型和 ONNX Runtime。模型由用户选择后台下载，约 20 MB，支持进度显示和断点续传；识别完全在本机完成。

模型把笔迹按实际包围盒居中成图像，因此在画布上写偏不改变输入坐标。连笔识别依赖 PP-OCRv6 对手写体的泛化能力，仍需用真实笔迹验证。
模型：https://www.modelscope.cn/models/RapidAI/RapidOCR/

pinyin.txt 来自 mozillazg/pinyin-data 的通规字常用读音，用于候选注音，多音字不做上下文判断。

识别质量尚需真实笔迹验证，尤其是连笔、潦草笔迹和模型未收录的字。
