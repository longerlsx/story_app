# 离线男声依赖与资源准备

本页维护离线语音资源的来源、固定版本、准备方式及分发条件。产品进度、设备结论和当前限制见[当前状态](../current-state.md)，实施过程见[听书计划](../superpowers/plans/2026-09-20-offline-listening.md)。

## 当前用途与选择

当前资源用于 **个人自用的离线听书**，按用户明确要求优先把功能和体验做好。记录依赖的真实来源与许可便于维护，不因此增加本轮验收门，也不替 Story App 选择或变更项目许可证。

生产资源选择改为 Kokoro 中文 v1.1 的 **FP32原始精度模型**，仍使用 sherpa-onnx 1.13.8 在 Android 本地生成音频。先前int8候选在手机上生成余量不足；同一Android运行库的模拟器对照中，原始精度模型显著更快，代价是模型文件更大。量化减小存储不等于在特定CPU和运行库上更快，不能以包体代替性能检查；样本与设备边界由[实施记录](../superpowers/plans/2026-09-20-offline-listening.md#真机反馈后的局部修复)维护。

男声 SID 59（`zm_010`）、58（`zm_009`），女声 SID 3（`zf_001`）、4（`zf_002`）保持不变，四种共用一份模型。FP32与原int8官方包的voices、tokens、中英词典和三个FST逐文件相同，不依赖系统TTS。两段男声的先前真实输出已提供用户试听；音色映射不代替新版播放和设备体验验收。[模型和音色说明](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html)

```mermaid
flowchart LR
    U[固定版本的官方 AAR 和模型包] --> V[校验 SHA256]
    V --> L[本机 .local-tts]
    L --> A[构建时加入 Story App APK]
    A --> G[手机为新正文生成语音]
```

构建准备可以联网；手机正常听书不需要从服务器获取新语音。二进制依赖保留在本机，不提交 Git；APK 仍以应用内包含模型为当前试验路径。

## 固定资源与构建入口

| 资源 | 来源 | 官方下载字节数 | SHA256 |
| --- | --- | ---: | --- |
| `sherpa-onnx-1.13.8.aar` | [官方发布](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar) | 50,129,134 | `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` |
| `kokoro-multi-lang-v1_1.tar.bz2` | [官方发布](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_1.tar.bz2) | 364,816,464 | `a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad` |

资源准备使用 [prepare-offline-voice.sh](../../scripts/prepare-offline-voice.sh)：

两项摘要已于 2026-09-20 对完整下载执行 SHA256 并与官方发布值核对一致。

```sh
# 已下载时直接复用该目录，避免重复下载；目录内文件名须与上表一致。
scripts/prepare-offline-voice.sh /path/to/completed-downloads

# 没有本地下载时，脚本从固定官方地址下载，并复用其本机下载缓存。
scripts/prepare-offline-voice.sh
```

脚本验证输入，保留模型原包内全部资源及 `LICENSE`，在暂存目录准备完成后替换目标。已有输入校验失败时不覆盖它，也不发布部分模型。成功切换后删除脚本管理的旧int8资源目录，避免两份模型同时打包。资源准备与 Gradle 构建串行执行，不在构建中途更换资源。

输出固定为：

```text
.local-tts/
  runtime/sherpa-onnx-1.13.8.aar
  assets/kokoro-multi-lang-v1_1/
```

其中模型实际需要 `model.onnx`、`voices.bin`、`tokens.txt`、中文／英文词典、`espeak-ng-data`；数字、日期、电话规范化使用附带的 FST 文件。不要只复制 ONNX 后将其余资源当成可选。当前保留完整原包，不凭目录名删除前端数据。FP32 ONNX为325,631,784字节，SHA256 `acc4adc175b9d9986106cd20060329673ad5a2e12ef3c557d2d3745b694f8b38`；原int8为114,299,010字节，音色数据仍为53,790,720字节。这些模型文件大小不是APK增量，最终包体另按实际构建记录。

Android 使用 `OfflineTts(assetManager, config)` 时，模型、词典、FST 可通过 `AssetManager` 读取；`espeak-ng-data` 要先复制到应用自己的文件目录，再将该实际路径传给 `dataDir`。这是上游前端对 Android 资源路径的明确约定。[对应实现](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/kokoro-multi-lang-lexicon.cc#L49-L59)

官方 AAR 包含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`，便于真机和模拟器使用同一版本。应用最终包含哪些 ABI 由构建配置决定；不能把全部 ABI 的 AAR 大小当成单台手机所需安装增量。[官方打包流程](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/android.yaml)

## 依赖来源与许可记录

| 层次 | 已核对事实 | 需保留的来源信息 |
| --- | --- | --- |
| Kokoro 源模型与音色 | [源模型卡](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh)标 Apache-2.0；作者说明中文数据由龙猫数据许可提供 | 保留原包内 `LICENSE`、模型卡及量化包来源 |
| sherpa-onnx 1.13.8 | [自身代码](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE)为 Apache-2.0 | 固定版本许可及 notices；该许可不能概括整份 AAR |
| ONNX Runtime 1.28.2 | [自身代码许可](https://github.com/microsoft/onnxruntime/blob/v1.28.2/LICENSE)为 MIT，版本由 sherpa Android 构建固定 | 许可及该版本 `ThirdPartyNotices.txt` |
| piper-phonemize | sherpa 固定 `f3ff95afc03640bc1399e113e83361192a2fafb4`，其[许可](https://github.com/csukuangfj/piper-phonemize/blob/f3ff95afc03640bc1399e113e83361192a2fafb4/LICENSE.md)为 MIT | 固定提交的许可，eSpeak 另有许可 |
| eSpeak NG | sherpa 固定 `ed530aa113046142eb5115cf2fc9157854d0ffe1`，其[许可](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING)为 GPL-3.0 | 固定提交的许可及它编入 JNI 的事实 |

上述依赖关系来自固定版本的 [TTS 构建入口](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/CMakeLists.txt)、[eSpeak 固定配置](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake)和 [Kokoro 前端](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/kokoro-multi-lang-lexicon.cc)。前端实际初始化 eSpeak 并处理部分非中文文本。维护者提出在未来 2.0 移除该依赖，当前固定版本仍包含它。[维护者说明](https://github.com/k2-fsa/sherpa-onnx/issues/3731)

准备脚本原样保留模型包内许可，其他依赖按本表的固定版本保存来源与原声明；不改写第三方许可。未来若改为公开分发，再按实际二进制、附属资源和分发方式核对对应条件，本轮个人自用不另设该门槛。
