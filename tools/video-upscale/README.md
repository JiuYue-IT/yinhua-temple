# 视频超分工具 (Real-ESRGAN)

用 [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN) 提升视频清晰度。

## 原理

```
视频 ──ffmpeg抽帧──> PNG 帧序列 ──Real-ESRGAN逐帧超分──> 放大帧 ──ffmpeg封装──> 新视频
                                                                        (音轨直接复制)
```

视频超分本质是「逐帧图像超分 + 重新封装」，因为 Real-ESRGAN 是图像模型，本身不认识时间维度。

## 安装

1. **ffmpeg**（必需）

   ```powershell
   winget install Gyan.FFmpeg
   ```

   装完重开终端，`ffmpeg -version` 能输出版本号即可。

2. **realesrgan-ncnn-vulkan**（默认后端，推荐）

   下载 windows 包，解压，把里面 `realesrgan-ncnn-vulkan.exe` 所在目录加入 PATH：

   https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-windows.zip

   解压后的目录结构里要保留 `models/` 文件夹（权重在里面）。
   不想配 PATH 就每次加 `--ncnn-bin "D:\path\to\realesrgan-ncnn-vulkan.exe"`。
   这个后端靠 Vulkan 调 GPU，不挑显卡，也不用装 CUDA。

3. **Python 后端**（可选，不装也能用）

   ```powershell
   pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
   pip install realesrgan basicsr opencv-python-headless
   ```

## 用法

```powershell
# 720p -> 1080p，最常用
python upscale_video.py -i input.mp4 -o output.mp4 --target-height 1080

# 先试 10 秒，确认效果和速度
python upscale_video.py -i input.mp4 -o test.mp4 --duration 10 --target-height 1080

# 二次元动画，2 倍
python upscale_video.py -i anime.mp4 -o anime_hd.mp4 -n realesr-animevideov3 -s 2

# 一条命令跑完 4 倍 + 用显卡编码器封装
python upscale_video.py -i in.mp4 -o out.mp4 --codec nvenc-h265 --crf 20

# 显存不够
python upscale_video.py -i in.mp4 -o out.mp4 --tile 128

# Python/torch 后端
python upscale_video.py -i in.mp4 -o out.mp4 --backend python --tta
```

`python upscale_video.py --help` 看全部参数。

## 常用参数速查

| 参数 | 说明 |
| --- | --- |
| `-n / --model` | 模型名，`--list-models` 可列 |
| `-s / --scale` | 超分倍数 2/3/4，默认 4 |
| `--target-height` | 输出目标高度，超分后再 lanczos 缩下去，画质比直接 2 倍好 |
| `--tile` | 分块大小，显存/内存不足时设 128 或 256 |
| `--tta` | 更锐，约 8 倍耗时 |
| `--codec` | `h264`(默认) / `h265` / `nvenc-h264` / `nvenc-h265` |
| `--crf` | 画质，越小越清晰，默认 17 |
| `--duration` | 只处理前 N 秒，调参时用 |
| `--keep-frames` | 保留中间帧，便于中断后断点续跑 |

## 注意事项

- **磁盘**：中间 PNG 帧很大。1080p 输出约 2–3 MB/帧，1 分钟 30fps 视频约 5 GB。脚本启动时会估算并提示。
- **中断续跑**：超分阶段已处理的帧不会重做。Ctrl+C 中断后用同样的命令再跑一次即可继续（别删工作目录，或加 `--keep-frames`）。
- **音轨**：默认 `-c:a copy` 原样复制；容器不支持时自动退回 AAC。
- **可变帧率(VFR)**：手机录屏常见。脚本会检测并提示，必要时用 `--fps 30` 显式指定。
- **HDR**：默认转 `yuv420p` 会丢 HDR。需要保留时加 `--pix-fmt yuv420p10le`，并自行补充色彩元数据参数。
- **速度参考**：RTX 5070 Laptop + ncnn + `realesrgan-x4plus` x4，1080p 帧大约 3–6 帧/秒；一段 1 分钟 30fps 的视频约需 10–20 分钟。`realesr-animevideov3` 快 3–5 倍。
