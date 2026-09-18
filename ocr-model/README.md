# ocr-model

旧版数字识别模型的训练与验证（历史研究模块）。

从 v2.8.0 起，Android 截图导入改用内置 ML Kit 中文文字识别，再提取码、取件点和地址并归纳为本地清单。本目录的数字 CNN 不再用于当前截图入口；保留代码和回归测试便于追溯。以下合成数据指标不代表新版中文识别准确率。

## 背景

本模块仅研究 4–8 位连续数字串，不能覆盖所有平台的取件码格式（例如字母码、横线分段码）。
本模块用合成数据训练一个轻量 CNN，识别单个数字字符；99.75% 是 Python 合成数字图管线的整串准确率，不是 Android 实际截图准确率。

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

## Android 集成（已完成）

- `export_weights.py` 将 PyTorch 权重导出为 `app/src/main/assets/pickup_ocr.bin`（约 400 KB）
- `TinyCnn.java` 提供纯 Java 卷积、池化和全连接推理，避免引入 TFLite / ONNX 依赖
- `PickupOcr.java` 完成图片预处理、连通域分割、字符归一化和结果校验
- `MainActivity.java` 负责选图、后台识别、人工核对修改、复制和菜鸟跳转
- `gen_test_vectors.py` 生成 PyTorch 对照向量，`TinyCnnTest.java` 校验 Java 推理结果
- `PickupOcrTest.java` 从像素经过生产预处理、分割和候选选择，验证明暗背景、完整码长、长号码拒绝、相邻数字与取消任务；字形资源由 `tools/OcrFixtureGenerator.java` 生成并入库，CI 不依赖系统字体

这些回归测试用于捕捉代码缺陷，不是代表性真实截图准确率测评。全屏文字、多个数字串、照片角度与复杂背景仍需要真实样本验证。

安装 JDK 17 与 Android SDK 35 后，在仓库根目录运行：

```powershell
.\gradlew.bat test
```
