#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Real-ESRGAN 视频超分脚本
========================

流程:  视频 --ffmpeg--> PNG 帧序列 --Real-ESRGAN--> 放大帧 --ffmpeg--> 新视频(保留原音轨)

两条后端(用 --backend 选择):
  ncnn  (默认) 需要 realesrgan-ncnn-vulkan.exe, 零 Python 依赖, Vulkan 走 GPU, 兼容性最好
  python        需要 torch + realesrgan + basicsr, 模型是 .pth, 可调参数更多

用法示例:
  # 最常用: 720p -> 1080p, 4 倍超分后再用 lanczos 缩到目标高度
  python upscale_video.py -i input.mp4 -o output.mp4 --target-height 1080

  # 二次元视频用专用模型, 2 倍即可
  python upscale_video.py -i anime.mp4 -o anime_hd.mp4 -n realesr-animevideov3 -s 2

  # 只做前 10 秒试跑, 确认效果和速度
  python upscale_video.py -i input.mp4 -o test.mp4 --duration 10 --target-height 1080

  # 用 Python/torch 后端 + 开启 TTA(更慢更锐)
  python upscale_video.py -i input.mp4 -o out.mp4 --backend python --tta

依赖:
  ffmpeg / ffprobe         必需, 需在 PATH 里
  realesrgan-ncnn-vulkan   ncnn 后端需要
  torch / realesrgan / basicsr / opencv-python   python 后端需要
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

# --------------------------------------------------------------------------- #
# 常量
# --------------------------------------------------------------------------- #

NCNN_MODELS = {
    "realesrgan-x4plus": "通用照片/实拍, 4 倍, 质量最好也最慢",
    "realesrgan-x4plus-anime": "二次元插画, 4 倍, 体积小速度快",
    "realesr-animevideov3": "二次元动画视频, 支持 2/3/4 倍, 速度极快",
    "realesrnet-x4plus": "不锐化的 4 倍重网络, 保留噪点细节",
}

# Python 后端模型注册表(缺失时自动从官方 Release 下载 .pth)
PY_MODELS = {
    "realesrgan-x4plus": dict(
        arch="rrdb", num_block=23, num_feat=64, netscale=4,
        file="RealESRGAN_x4plus.pth",
        url="https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth",
    ),
    "realesrgan-x4plus-anime": dict(
        arch="rrdb", num_block=6, num_feat=64, netscale=4,
        file="RealESRGAN_x4plus_anime_6B.pth",
        url="https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.2.4/RealESRGAN_x4plus_anime_6B.pth",
    ),
    "realesr-animevideov3": dict(
        arch="srvgg", num_conv=16, netscale=4,
        file="realesr-animevideov3.pth",
        url="https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth",
    ),
    "realesr-general-x4v3": dict(
        arch="srvgg", num_conv=32, netscale=4,
        file="realesr-general-x4v3.pth",
        url="https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth",
    ),
}

FRAME_DIR_IN = "in"        # 抽出来的原始帧
FRAME_DIR_OUT = "out"      # 超分后的帧
FRAME_DIR_CHUNK_IN = "chunk_in"
FRAME_DIR_CHUNK_OUT = "chunk_out"
FRAME_PATTERN = "%08d.png"


# --------------------------------------------------------------------------- #
# 小工具
# --------------------------------------------------------------------------- #

def log(msg: str = "") -> None:
    print(msg, flush=True)


def die(msg: str, code: int = 1):
    print(f"\n[错误] {msg}", file=sys.stderr, flush=True)
    sys.exit(code)


def run(cmd: list, quiet: bool = False, check: bool = True) -> subprocess.CompletedProcess:
    """执行外部命令, 统一处理编码/静默/错误。"""
    if not quiet:
        log("  $ " + " ".join(str(c) for c in cmd))
    return subprocess.run(
        [str(c) for c in cmd],
        stdout=subprocess.PIPE if quiet else None,
        stderr=subprocess.PIPE if quiet else None,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    ) if not check else subprocess.run(
        [str(c) for c in cmd],
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def which(name: str) -> str | None:
    return shutil.which(name)


def human_bytes(n: float) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(n) < 1024:
            return f"{n:.1f}{unit}"
        n /= 1024
    return f"{n:.1f}PB"


def human_time(sec: float) -> str:
    sec = int(max(0, sec))
    h, m, s = sec // 3600, (sec % 3600) // 60, sec % 60
    return f"{h}h{m:02d}m{s:02d}s" if h else f"{m}m{s:02d}s"


def progress(done: int, total: int, t0: float, prefix: str = "") -> None:
    pct = done / total * 100 if total else 0
    bar_len = 30
    filled = int(bar_len * done / total) if total else 0
    bar = "#" * filled + "-" * (bar_len - filled)
    elapsed = time.time() - t0
    eta = (elapsed / done * (total - done)) if done else 0
    line = f"\r{prefix}[{bar}] {done}/{total} ({pct:5.1f}%)  已用 {human_time(elapsed)}  剩余约 {human_time(eta)}"
    sys.stdout.write(line.ljust(100))
    sys.stdout.flush()
    if done >= total:
        sys.stdout.write("\n")


# --------------------------------------------------------------------------- #
# 视频探测 / 抽帧 / 合成
# --------------------------------------------------------------------------- #

def ffprobe_info(ffprobe: str, src: Path) -> dict:
    p = subprocess.run(
        [ffprobe, "-v", "error", "-print_format", "json",
         "-show_streams", "-show_format", str(src)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding="utf-8", errors="replace", check=False,
    )
    if p.returncode != 0:
        die(f"ffprobe 读不了这个文件: {p.stderr.strip()}")
    return json.loads(p.stdout)


def parse_fps(stream: dict) -> float:
    def to_f(r: str) -> float:
        try:
            num, _, den = str(r).partition("/")
            d = float(den) if den else 1.0
            return float(num) / d if d else 0.0
        except Exception:
            return 0.0

    return to_f(stream.get("avg_frame_rate") or "0/0") or to_f(stream.get("r_frame_rate") or "0/0")


def inspect(info: dict) -> dict:
    v = next((s for s in info["streams"] if s.get("codec_type") == "video"), None)
    if v is None:
        die("输入文件里没有视频流")

    r = parse_fps({"avg_frame_rate": v.get("r_frame_rate")})   # 标称帧率
    a = parse_fps(v)                                            # 平均帧率
    fps = a or r or 25.0

    has_audio = any(s.get("codec_type") == "audio" for s in info["streams"])
    dur = float(info.get("format", {}).get("duration") or v.get("duration") or 0.0)

    return {
        "width": int(v["width"]),
        "height": int(v["height"]),
        "fps": fps,
        "nominal_fps": r,
        "vfr": bool(r and a and abs(r - a) / r > 0.01),   # 可变帧率信号
        "nb_frames": int(v["nb_frames"]) if v.get("nb_frames", "N/A").isdigit() else 0,
        "duration": dur,
        "has_audio": has_audio,
        "codec": v.get("codec_name", "?"),
        "pix_fmt": v.get("pix_fmt", ""),
    }


def extract_frames(ffmpeg: str, src: Path, pattern: Path, args) -> None:
    common = ["-hide_banner", "-nostdin", "-y"]
    if args.start:
        common += ["-ss", str(args.start)]
    if args.duration:
        common += ["-t", str(args.duration)]
    common += ["-i", str(src), "-map", "0:v:0"]

    # ffmpeg >= 5.1 用 -fps_mode passthrough, 老版本退回 -vsync 0
    for mode in (["-fps_mode", "passthrough"], ["-vsync", "0"]):
        p = run([ffmpeg] + common + mode + [str(pattern)])
        if p.returncode == 0:
            return
    die("抽帧失败, 请检查 ffmpeg 版本与输入文件")


def assemble(ffmpeg: str, out_pattern: Path, fps: float, src: Path, dst: Path,
             info: dict, args) -> None:
    args_out = Path(args.out)
    args_out.parent.mkdir(parents=True, exist_ok=True)

    vf = []
    if args.target_height:
        vf.append(f"scale=-2:{args.target_height}:flags=lanczos")
    if args.target_width:
        vf.append(f"scale={args.target_width}:-2:flags=lanczos")

    codec = args.codec
    if codec == "h264":
        venc = ["-c:v", "libx264", "-crf", str(args.crf), "-preset", args.preset]
    elif codec == "h265":
        venc = ["-c:v", "libx265", "-crf", str(args.crf), "-preset", args.preset,
                "-tag:v", "hvc1"]
    elif codec == "nvenc-h264":
        venc = ["-c:v", "h264_nvenc", "-rc", "vbr", "-cq", str(args.crf),
                "-preset", "p6", "-b:v", "0"]
    elif codec == "nvenc-h265":
        venc = ["-c:v", "hevc_nvenc", "-rc", "vbr", "-cq", str(args.crf),
                "-preset", "p6", "-b:v", "0", "-tag:v", "hvc1"]
    else:
        die(f"未知编码器: {codec}")

    base = [ffmpeg, "-hide_banner", "-nostdin", "-y",
            "-framerate", f"{fps:.6f}", "-start_number", "1",
            "-i", str(out_pattern), "-i", str(src),
            "-map", "0:v:0", "-map", "1:a?", "-map_metadata", "1"]
    if vf:
        base += ["-vf", ",".join(vf)]
    base += venc + ["-pix_fmt", args.pix_fmt, "-movflags", "+faststart"]

    log("  封装中...")
    # 优先直接复制音轨(无损且即时), 容器/编码不兼容时退回 AAC
    p = run(base + ["-c:a", "copy", str(args_out)])
    if p.returncode != 0:
        log("  [提示] 音轨无法直接复制, 改用 AAC 重新编码")
        p = run(base + ["-c:a", "aac", "-b:a", "192k", str(args_out)])
        if p.returncode != 0:
            die("合成失败")


# --------------------------------------------------------------------------- #
# 后端 A: realesrgan-ncnn-vulkan
# --------------------------------------------------------------------------- #

def find_ncnn(explicit: str | None) -> str:
    if explicit:
        if Path(explicit).exists():
            return str(explicit)
        die(f"找不到 {explicit}")
    for name in ("realesrgan-ncnn-vulkan", "realesrgan-ncnn-vulkan.exe"):
        p = which(name)
        if p:
            return p
    die("未找到 realesrgan-ncnn-vulkan。\n"
        "  下载: https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-windows.zip\n"
        "  解压后把它所在目录加进 PATH, 或用 --ncnn-bin 指定 exe 的绝对路径。")


def ncnn_process(frames: list, binary: str, work: Path, args) -> None:
    """按 chunk 分批调用 ncnn-vulkan, 支持中断续跑。"""
    out_dir = work / FRAME_DIR_OUT
    chunk = max(1, args.chunk)
    todo = [f for f in frames if not (out_dir / f.name).exists()]
    if not todo:
        log("  所有帧都已处理过, 跳过")
        return

    log(f"  ncnn 模型 {args.model}, 缩放 x{args.scale}, tile={args.tile}, 共 {len(todo)} 帧待处理")
    mr = args.model_dir
    cmd_base = [binary]
    if mr:
        cmd_base += ["-m", str(mr)]
    cmd_base += ["-n", args.model, "-s", str(args.scale),
                 "-t", str(args.tile), "-g", str(args.gpu), "-f", "png",
                 "-j", args.jobs]
    if args.tta:
        cmd_base += ["-x"]

    done = len(frames) - len(todo)
    t0 = time.time()
    progress(done, len(frames), t0, "  超分 ")

    for i in range(0, len(todo), chunk):
        part = todo[i:i + chunk]
        cin, cout = work / FRAME_DIR_CHUNK_IN, work / FRAME_DIR_CHUNK_OUT
        shutil.rmtree(cin, ignore_errors=True)
        shutil.rmtree(cout, ignore_errors=True)
        cin.mkdir(parents=True)
        cout.mkdir(parents=True)

        for f in part:
            shutil.copy2(f, cin / f.name)

        p = run(cmd_base + ["-i", str(cin), "-o", str(cout)], quiet=True)
        if p.returncode != 0:
            die("realesrgan-ncnn-vulkan 执行失败。常见原因: 显卡驱动过旧 / 显存不足(试 -t 128) / 模型名写错")

        got = 0
        for f in part:
            produced = cout / f.name
            if produced.exists():
                shutil.move(str(produced), str(out_dir / f.name))
                got += 1
            else:                                   # 名字带后缀变化的兜底
                for cand in cout.iterdir():
                    if cand.stem.startswith(f.stem):
                        shutil.move(str(cand), str(out_dir / f.name))
                        got += 1
                        break
        if got != len(part):
            die(f"本批 {len(part)} 帧只产出 {got} 帧, 已停止")

        shutil.rmtree(cin, ignore_errors=True)
        shutil.rmtree(cout, ignore_errors=True)
        done += len(part)
        progress(done, len(frames), t0, "  超分 ")


# --------------------------------------------------------------------------- #
# 后端 B: Python / PyTorch
# --------------------------------------------------------------------------- #

def ensure_py_model(name: str, weights_dir: Path, model_path: str | None) -> Path:
    if model_path:
        p = Path(model_path)
        if not p.exists():
            die(f"找不到权重文件 {p}")
        return p
    spec = PY_MODELS[name]
    dst = weights_dir / spec["file"]
    if dst.exists() and dst.stat().st_size > 1_000_000:
        return dst
    weights_dir.mkdir(parents=True, exist_ok=True)
    log(f"  下载权重 {spec['file']} ...")
    try:
        urllib.request.urlretrieve(spec["url"], dst)
    except Exception as e:
        dst.unlink(missing_ok=True)
        die(f"权重下载失败({e})。请手动下载后放到 {dst}:\n  {spec['url']}")
    return dst


def build_py_upsampler(name: str, weights: Path, args):
    # 部分 torchvision 版本删掉了 basicsr 还在 import 的老模块, 打个补丁
    try:
        import torchvision.transforms.functional_tensor  # noqa: F401
    except Exception:
        import torchvision.transforms.functional as _F
        sys.modules["torchvision.transforms.functional_tensor"] = _F

    try:
        import torch
        from realesrgan import RealESRGANer
    except Exception as e:
        die(f"Python 后端依赖不完整: {e}\n"
            "  安装: pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128\n"
            "        pip install realesrgan basicsr opencv-python-headless")

    spec = PY_MODELS[name]
    if spec["arch"] == "rrdb":
        from basicsr.archs.rrdbnet_arch import RRDBNet
        net = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=spec["num_feat"],
                      num_block=spec["num_block"], num_grow_ch=32, scale=4)
    else:
        from realesrgan.archs.srvgg_arch import SRVGGNetCompact
        net = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64,
                              num_conv=spec["num_conv"], upscale=4, act_type="prelu")

    device = args.device or ("cuda" if torch.cuda.is_available() else "cpu")
    if device == "cpu":
        log("  [提示] 没有可用 CUDA, 走 CPU 会非常慢, 建议改用默认 ncnn 后端")

    return RealESRGANer(
        scale=args.scale,
        model_path=str(weights),
        model=net,
        tile=args.tile,
        tile_pad=args.tile_pad,
        pre_pad=0,
        half=(device == "cuda"),
        device=device,
        netscale=spec["netscale"],
    )


def py_process(frames: list, work: Path, args) -> None:
    import cv2

    weights = ensure_py_model(args.model, Path(args.weights_dir), args.model_path)
    upsampler = build_py_upsampler(args.model, weights, args)

    out_dir = work / FRAME_DIR_OUT
    todo = [f for f in frames if not (out_dir / f.name).exists()]
    if not todo:
        log("  所有帧都已处理过, 跳过")
        return

    log(f"  torch 模型 {args.model}, 缩放 x{args.scale}, tile={args.tile}, TTA={args.tta}, 共 {len(todo)} 帧")
    done = len(frames) - len(todo)
    t0 = time.time()
    progress(done, len(frames), t0, "  超分 ")

    for f in todo:
        img = cv2.imread(str(f), cv2.IMREAD_UNCHANGED)
        if img is None:
            die(f"读不了帧 {f}")
        try:
            out, _ = upsampler.enhance(img, outscale=args.scale)
        except RuntimeError as e:
            if "CUDA out of memory" in str(e):
                die("显存不足。加 --tile 256 (或 128) 再试")
            raise
        cv2.imwrite(str(out_dir / f.name), out)
        done += 1
        progress(done, len(frames), t0, "  超分 ")


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="用 Real-ESRGAN 给视频超分(提清晰度)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="模型说明(ncnn):\n" + "\n".join(f"  {k:<26}{v}" for k, v in NCNN_MODELS.items()),
    )
    g = p.add_argument_group("输入输出")
    g.add_argument("-i", "--input", help="输入视频路径")
    g.add_argument("-o", "--out", help="输出视频路径")
    g.add_argument("--work", default=None, help="工作目录(默认 <输出名>.frames)")

    g = p.add_argument_group("超分参数")
    g.add_argument("-n", "--model", default="realesrgan-x4plus", help="模型名")
    g.add_argument("-s", "--scale", type=int, default=4, choices=[2, 3, 4],
                   help="超分倍数(ncnn 支持 2/3/4; python 后端任意)")
    g.add_argument("--backend", default="ncnn", choices=["ncnn", "python"])
    g.add_argument("--tta", action="store_true", help="TTA 模式, 更锐但约 8 倍耗时")
    g.add_argument("--tile", type=int, default=0, help="分块大小, 0=自动; 显存不足设 128/256")
    g.add_argument("--tile-pad", type=int, default=10, help="python 后端分块重叠")
    g.add_argument("--gpu", type=int, default=0, help="ncnn 使用的 GPU id")
    g.add_argument("--jobs", default="2:4:2", help="ncnn 的 load:proc:save 线程数")
    g.add_argument("--chunk", type=int, default=300, help="ncnn 每批处理帧数(便于续跑)")
    g.add_argument("--ncnn-bin", default=None, help="realesrgan-ncnn-vulkan 路径")
    g.add_argument("--model-dir", default=None, help="ncnn 的 models 目录")
    g.add_argument("--weights-dir", default="weights", help="python 后端权重目录")
    g.add_argument("--model-path", default=None, help="python 后端自定义 .pth 路径")
    g.add_argument("--device", default=None, help="python 后端 cuda / cpu")

    g = p.add_argument_group("输出视频")
    g.add_argument("--target-height", type=int, default=None, help="目标高度, 如 1080; 会 lanczos 缩放")
    g.add_argument("--target-width", type=int, default=None, help="目标宽度")
    g.add_argument("--codec", default="h264",
                   choices=["h264", "h265", "nvenc-h264", "nvenc-h265"], help="输出编码器")
    g.add_argument("--crf", type=float, default=17, help="画质(越小越好), nvenc 下即 cq")
    g.add_argument("--preset", default="slow", help="x264/x265 preset")
    g.add_argument("--pix-fmt", default="yuv420p", help="像素格式, HDR 可用 yuv420p10le")
    g.add_argument("--fps", type=float, default=None, help="覆盖帧率(默认按源视频)")

    g = p.add_argument_group("裁剪与调试")
    g.add_argument("--start", default=None, help="起始时间, 如 00:00:30")
    g.add_argument("--duration", default=None, help="处理时长, 如 10 或 00:00:10")
    g.add_argument("--keep-frames", action="store_true", help="保留中间帧目录")
    g.add_argument("--list-models", action="store_true", help="列出模型后退出")
    return p


def main() -> None:
    args = build_parser().parse_args()

    if args.list_models:
        log("ncnn 后端可用模型:")
        for k, v in NCNN_MODELS.items():
            log(f"  {k:<26}{v}")
        log("\npython 后端可用模型:")
        for k in PY_MODELS:
            log(f"  {k}")
        return

    if not args.input or not args.out:
        die("必须提供 -i 输入视频 与 -o 输出路径  (python upscale_video.py --help 查看用法)")

    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    src = Path(args.input)
    if not src.exists():
        die(f"输入文件不存在: {src}")

    ffmpeg, ffprobe = which("ffmpeg"), which("ffprobe")
    if not ffmpeg or not ffprobe:
        die("未找到 ffmpeg/ffprobe。Windows 可执行:  winget install Gyan.FFmpeg   "
            "(装完重开终端); 或从 https://www.gyan.dev/ffmpeg/builds/ 下载后把 bin 加入 PATH")

    if args.backend == "ncnn":
        binary = find_ncnn(args.ncnn_bin)
    else:
        binary = None

    # ---- 探测 ----
    log("=" * 78)
    info = inspect(ffprobe_info(ffprobe, src))
    fps = args.fps or info["fps"]
    log(f"输入   : {src}")
    log(f"分辨率 : {info['width']}x{info['height']}  ({info['codec']}, {info['pix_fmt']})")
    log(f"帧率   : {fps:.4f} fps   时长: {human_time(info['duration'])}   音轨: {'有' if info['has_audio'] else '无'}")
    if info["vfr"]:
        log(f"[提示] 疑似可变帧率(标称 {info['nominal_fps']:.3f} / 平均 {info['fps']:.3f}), "
            f"输出将按 {fps:.3f} 恒定帧率封装; 若出现音画不同步请用 --fps 指定标称值")
    out_h = args.target_height or info["height"] * args.scale
    out_w = args.target_width or round(info["width"] * args.scale / 2) * 2
    log(f"输出   : ~{out_w}x{out_h}  后端={args.backend}  模型={args.model}  x{args.scale}")

    work = Path(args.work) if args.work else Path(str(args.out) + ".frames")
    in_dir, out_dir = work / FRAME_DIR_IN, work / FRAME_DIR_OUT
    in_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    log(f"工作区 : {work.resolve()}")
    log("=" * 78)

    t_all = time.time()

    # ---- 1. 抽帧 ----
    frames = sorted(in_dir.glob("*.png"))
    if frames and not args.start and not args.duration:
        log(f"[1/3] 抽帧: 已有 {len(frames)} 帧, 跳过")
    else:
        shutil.rmtree(in_dir, ignore_errors=True)
        in_dir.mkdir(parents=True, exist_ok=True)
        log("[1/3] 抽帧...")
        t0 = time.time()
        extract_frames(ffmpeg, src, in_dir / FRAME_PATTERN, args)
        frames = sorted(in_dir.glob("*.png"))
        if not frames:
            die("没有抽到任何帧")
        log(f"      共 {len(frames)} 帧, 耗时 {human_time(time.time() - t0)}")

    est = len(frames) * (info["width"] * args.scale) * (info["height"] * args.scale) * 3 * 1.1
    log(f"      提示: 中间帧约占磁盘 {human_bytes(est)}, 请确认空间充足")

    # ---- 2. 超分 ----
    log(f"[2/3] 超分({args.backend} 后端)...")
    if args.backend == "ncnn":
        ncnn_process(frames, binary, work, args)
    else:
        py_process(frames, work, args)

    # ---- 3. 合成 ----
    log("[3/3] 重新编码并封装...")
    assemble(ffmpeg, out_dir / FRAME_PATTERN, fps, src, Path(args.out), info, args)

    if not args.keep_frames:
        shutil.rmtree(work, ignore_errors=True)
        log("      已清理中间帧目录(下次想续跑请加 --keep-frames)")

    out = Path(args.out)
    log("=" * 78)
    log(f"完成 ✓  {out}  ({human_bytes(out.stat().st_size)})")
    log(f"总耗时 {human_time(time.time() - t_all)}")
    log("=" * 78)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("\n已中断。中间帧还留在工作目录里, 重新执行同一命令即可续跑。")
        sys.exit(130)
