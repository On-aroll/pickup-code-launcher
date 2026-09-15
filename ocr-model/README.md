# ocr-model

取件码识别模型的训练与验证（独立于 Android 主工程）。

## 背景

取件码（菜鸟 / 丰巢 / 京东 / 顺丰等）为 4–8 位纯数字串，通常以短信或截图形式出现。
本模块用合成数据训练一个轻量 CNN，识别单个数字字符，端到端管线在整串取件码上的准确率为 99.75%。

## 管线

```
截图/拍照 → 灰度化 → 中值滤波 → 二值化 → 连通域分割（合并分裂笔画） → 字符归一化(28×28) → CNN 推理 → 数字串校验
```

- 预处理与分割全部可用纯 Java 重写（无 OpenCV 依赖）
- CNN 结构：conv3×3(8)→ReLU→pool2 → conv3×3(16)→ReLU→pool2 → fc(128) → fc(10)
- 输入 28×28 灰度（数字=1，背景=0），输出 0–9 分类

## 指标（合成数据验证）

| 项目 | 数值 |
| --- | --- |
| 单字符识别 | 99.93% |
| 整串取件码（4–8 位，2000 例） | 99.75% |
| 分割（连通域+合并） | 100% |

## 文件

- `generate_data.py`：合成训练数据（多字体、旋转、模糊、噪声、阈值抖动），输出到 `data/`（不入库）
- `train.py`：训练 CNN，导出 `models/pickup_cnn.pt` 与 `models/pickup_cnn_weights.npz`（浮点权重，供 Java 推理内核使用）
- `verify_pipeline.py`：端到端验证（渲染整串 → 分割 → 识别 → 统计整串准确率）
- `debug_seg.py`：分割调试与样本可视化

## 复现

```bash
pip install pillow numpy torch scipy
python generate_data.py
python train.py
python verify_pipeline.py
```

## Android 集成（下一步）

- 权重约 376 KB，可直接打包进 APK
- 推理内核用纯 Java 实现（卷积/池化/全连接手写，约 100 行），避免引入 TFLite / ONNX 依赖
