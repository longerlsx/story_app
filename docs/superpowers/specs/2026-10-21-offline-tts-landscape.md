# 离线中文听书与持续低负载：方案调查报告

日期：2026-10-21。性质：资料、源码和既有项目证据调查；不是引擎选定或实施计划。项目代码基线为 `d9a9f2c`，APK 身份仍由 [current-state.md](../../current-state.md) 维护。本轮没有安装应用、下载大模型、操作手机、运行新合成实验或更换产品引擎。

## 判断与建议

**离线中文听书有现实可行的路线，值得调整选型方向。当前最值得投入的是寻找适合持续朗读的轻量音源，以及核实可用的手机硬件加速，而不是继续围绕 ZipVoice 减步数、截参考音频，或再做一轮没有针对性的 App 整理。**

本轮沿阅读器源码、系统语音接口、中文模型及文本前端、手机推理移植、GPU／NPU、商业离线 SDK、传统低计算合成几条线交叉检索。既查可直接接入的候选，也查只有工程借鉴价值的相近项目。下面按“中文能否完整读通、持续计算是否有余量、Android 链路是否成立”排序，不按发布时间、参数量或宣传音质排序。

建议把**中文 Piper、Matcha 中文及其轻量声码器、小型中文 VITS**作为轻量路线的主要比较对象；其中有女声、低采样率或中文前端的取舍，不能预先宣布胜出。**Bert-VITS2-MNN**值得作为另一条完整移动端工程候选，**MOSS-TTS-Nano和高通NPU**保留给需要克隆声音的方向。后几项接入和验证成本较高。系统离线声音适合作实际体验对照；商业 SDK 能解释行业为什么早已可以离线朗读，却不符合当前免费开源优先方向，不作为默认替换。

没有查到任何可直接证明“小米 15 Ultra 和 17 Pro、中文小说、1.45×、长听不明显发热”的公开完整结果。这个缺口不否定路线可行，只意味着研究能够缩小选择，不能代替最终手机体验。

## 1. 本轮实际需求和新反馈

用户确认两台手机均在 **1.45×** 出现发热，并观察到声音随发热出现失真；对照包括小米自带浏览器、番茄及未具体指明的网盘 App，断网、导入本地小说仍能朗读。四个核心要求是：

- 手机本地为新的正文生成声音，断网也可用。
- 中文读得通顺、完整，长听没有滋滋声、漏读、截尾及异常停顿。
- 持续运算消耗低，日常长听不明显发热。
- 复用成熟方案，个人免费使用优先。

此前的中文男声、单一雷军合成音色和尽量一体打包仍是已有选择；本轮广泛调查不把“必须实时克隆同一声音”作为隐含筛选门槛，但也**没有擅自批准改成女声、系统依赖或收费 SDK**。若轻量候选需要这些取舍，报告明确列出。

本次没有读取两台手机正在使用的 APK 身份、声音配置或温度日志。新反馈是有效的实际体验证据，尚不是“升温直接导致模型算错声音”的因果证明。10月16日固定样本与长听摘录的核听通过继续作为当时窗口的证据，不能覆盖本次投诉。问题继续归入 [BUG-062](../../knowledge/bugs/BUG-2026-062-zipvoice-long-listening-thermal.json)，不与模拟器上那次未定位交接停顿 [BUG-066](../../knowledge/bugs/BUG-2026-066-intermittent-audio-handoff-delay.json) 混为同一根因。

## 2. 当前方案为什么值得重新选型

当前生产链路是 ZipVoice-Distill INT8、四线程、三步、完整 6.057 秒参考，先生成自然 1× 音频，再由 AudioTrack 按 1.45× 播放。ZipVoice 是零样本声音克隆模型，每段合成都依赖参考音频和文字；普通固定说话人模型可以不用这套参考条件。架构不同，有降低计算的理由，但不能仅凭这一点计算节电百分比。[ZipVoice 官方说明](https://github.com/k2-fsa/ZipVoice)、[项目实际依赖与调用边界](../../knowledge/offline-voice-dependencies.md)。

已有真机调查给出的方向很清楚：四步基线的有界采样中，超过 98% 的用户态 CPU 样本落在 ONNX Runtime；另一次模型 profiling 中，Decoder 占记录到的模型 Run 耗时约 94.8%。两者不是同一口径，更不是能耗百分比，但足以把模型计算列为主要调查对象。此前没有发现无界预取、暂停后持续新生成等可以解释大部分消耗的应用层行为。[9月23日证据与已撤回路线](../../knowledge/2026-09-23-offline-tts-thermal-handoff.md)。

1.45× 会直接压缩供给余量：10 秒自然语音只播放约 **6.90 秒**；若生成要 6～7 秒，手机几乎一直在合成。此时多预取几段只能推迟耗尽缓存，不能消除每段计算。用“生成耗时÷自然音频时长”定义 RTF 时，持续供给的必要条件是小于 `1 / 1.45 ≈ 0.69`，实际还需给长句、调度、后台和温态降速留余量。这是当前“自然合成再快放”路径的推导，不是任何模型都能直接套用的热测试标准。

已有失败尝试不再包装成新方向：三线程没有取得可接受真机收益；两步和短参考都被用户后续听感否定。Kokoro 也不是未尝试过的新品：项目曾从 INT8 改为更快的 FP32，15 Ultra 某段 1.45× 样本的生成／自然音频比中位数仍约 0.643；长期温耗没有因此通过。后来改 ZipVoice，主要是用户选择了该声音。[Kokoro 当时的测量](../plans/2026-09-20-offline-listening.md)、[声音选择背景](2026-09-22-listening-controls-and-voice.md)。

## 3. 别人的离线朗读究竟怎样做

“小说 App 支持断网朗读”完全可能；它不要求每个阅读器都内置实时克隆模型。公开实现中常见的是以下分工：

```mermaid
flowchart LR
    A[本地小说与阅读位置] --> B[阅读器分句与播放调度]
    B --> C[Android 系统 TTS 接口]
    B --> D[App 内置离线模型]
    C --> E[已安装的离线语音引擎与音色]
    D --> F[本地 PCM]
    E --> F
    F --> G[音频输出与进度回调]
```

| 对象 | 已核实的实际做法 | 能学什么，不能据此断言什么 |
| --- | --- | --- |
| Librera | 官方 `TTSEngine` 创建 Android `TextToSpeech`、选择已安装引擎、排队朗读或生成文件；另有服务管理正文和进度 | 阅读器与音源可分开；是否离线、用多少算力由所选音源决定。[源码](https://raw.githubusercontent.com/foobnix/LibreraReader/master/app/src/main/java/com/foobnix/tts/TTSEngine.java) |
| Sherpa 官方 Android TTS Engine | `TextToSpeechService` 调本地 `OfflineTts`，经回调返回 PCM；有预制离线 APK | 同一模型可嵌入 App，也可包装为系统引擎。换壳本身不减少模型计算。[服务源码](https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt)、[官方 APK](https://k2-fsa.github.io/sherpa/onnx/android/prebuilt-apk.html) |
| Legado／阅读 | 原作者历史教程确有在线 TTS；原 `gedoor/legado` 当前仓库已无原实现代码。可见续仓含系统和 HTTP 两路，但本轮未确认续仓官方身份 | 不能从“阅读器开源”推定“有自带开源离线中文模型”，也不拿已删除的主仓库假装核实了当前源码。[原教程](https://gedoor.github.io/blog/pork-hock)、[原仓库](https://github.com/gedoor/legado) |
| jing332 TTS Server | 插件可调用网络 TTS，也可把 Android 系统声音转为本地 HTTP 供阅读器缓存 | HTTP 只是接口；`127.0.0.1` 可以完全本地，远端 Edge 等声音仍需网络。缓存不等于对新文本离线生成。[官方帮助](https://raw.githubusercontent.com/jing332/tts-server-android/compose/app/src/main/assets/help/app.md) |
| HayaiTTS／VoxSherpa | 多种本地模型的 Android 系统引擎封装；Hayai 当前系统服务先完整生成，再分块输出 | 可学模型管理与接口接线。没有足够中文长听温耗数据；“2020年后手机亚秒”“任何手机都快”等宣传不能代替测量。[Hayai 服务](https://raw.githubusercontent.com/HayaiApp/HayaiTTS/main/app/src/main/java/dev/ahmedmohamed/hayaitts/tts/HayaiTtsService.kt)、[VoxSherpa](https://github.com/CodeBySonu95/VoxSherpa-TTS) |

小米官方商店确有 `com.xiaomi.mibrain.speech` 系统语音引擎；Android API 可以枚举实际引擎、声音和声音是否要求网络。不过商店页面较旧，**本轮没有取得浏览器、番茄及用户所指网盘当前朗读模块的一手实现资料**，不能猜它们统一使用小爱、讯飞、某个开源模型或 NPU。用户的体验说明值得找对照，公开资料则只支持以上架构可能性。[小米官方页面](https://app.mi.com/details?id=com.xiaomi.mibrain.speech)、[Android Voice](https://developer.android.com/reference/android/speech/tts/Voice)、[TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)。

## 4. 更贴近本项目的轻量中文候选

表内大小是具体模型文件或组件大小，不是最终 APK、内存峰值或计算量。所有候选都没有本轮两台手机的持续温耗实测。

| 候选 | 中文、声音与接入 | 直接有用的依据 | 取舍和建议位置 |
| --- | --- | --- | --- |
| **VITS icefall AISHELL3 low** | 固定 174 个说话人；模型约 30.5MB，**8kHz**；已有 sherpa Android 路径 | 官方 Pi4 上 RTF 1线程0.365／4线程0.156，有明显轻量化线索 | 首批“低计算基线”。8kHz 高频范围有限，可能偏电话音，必须先核听，不能当作当前24kHz声音的无损替代。不是旧121MB AISHELL3。[官方模型说明](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#aishell3-chinese-multi-speaker-174-speakers)、[权重](https://huggingface.co/csukuangfj/icefall-tts-aishell3-vits-low-2024-04-06) |
| **Matcha icefall 中文 Baker** | 单女声、22.05kHz；声学约75.6MB，官方示例另配约51MiB Vocos；有 Kotlin／Java／Android APK | 官方 Pi4 三步版 RTF 1线程0.892／4线程0.391 | 首批自然度与计算折中的候选，但女声不满足原男声偏好；须用 icefall 导出版，不能直接拿任意 Matcha 权重。[官方部署与样音](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/matcha-icefall-zh-baker.html) |
| **Piper 中文 xiao_ya medium** | 单音色、22.05kHz、ONNX约63.2MB；sherpa已有中文词典／规则和 Android 包 | 成熟的固定音色架构，资源比当前完整克隆链小；本轮没查到该中文权重的可信手机热态基准 | 首批备选；性别和听感以样音为准。原 Python 前端依赖不能直接等同 sherpa 中文前端；不能引用英语 Piper 的速度当中文成绩。[模型文件](https://huggingface.co/rhasspy/piper-voices/tree/main/zh/zh_CN/xiao_ya/medium)、[Android与样音](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/vits-piper-zh_CN-xiao_ya-medium.html) |
| Piper 中文 huayan medium | 单音色、22.05kHz、主模型约63.2MB；sherpa转换包 | 可作第二音色比较；未查到目标手机温耗证据 | 后备；单音色卡将训练数据许可写为Unknown，不能拿voices仓库顶部MIT覆盖。[单音色卡](https://huggingface.co/rhasspy/piper-voices/blob/main/zh/zh_CN/huayan/medium/MODEL_CARD)、[转换包](https://huggingface.co/csukuangfj/vits-piper-zh_CN-huayan-medium) |
| MeloTTS 中文／中英 | 单音色、44.1kHz；转换仓库有约170MB FP32和53.5MB INT8；有 Android 接入 | FP32在同一Pi4四线程表中RTF2.518；INT8不能沿用这一数字 | 第二批，尤其关注INT8是否真正适合ARM；“CPU可实时”或文件小不构成低热结论。[原中文权重](https://huggingface.co/myshell-ai/MeloTTS-Chinese)、[转换文件](https://huggingface.co/csukuangfj/vits-melo-tts-zh_en/tree/main) |
| Kokoro 中文 v1.1 | 中文男女声齐备、24kHz；FP32主模型约311MiB，INT8还有独立声音资源 | 本项目已有INT8反而更慢、改FP32后仍余量有限的记录 | 不重复原CPU路线。只有明确改变执行后端、证明更高效的实现才值得重新进入；NPU英文实现见下节。[官方中文声音](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html)、[中文权重](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh) |

上述 Pi4 数字来自 sherpa 官方同一设备表，是生成耗时／音频时长，不是手机数据或能耗。该表还列出中文 `zh-ll`、`fanchen-C`、`fanchen-wnj` 等，四线程 RTF 约1.59～1.61，说明**“VITS”这个名字本身也不保证低计算**。不同权重、采样率、声码器和前端都必须固定。[官方 RTF 表](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/rtf.html)。

进一步追查发现，Matcha并非只能搭较大的Vocos：官方还列出HiFiGAN v1／v2／v3，发布资产中的v2约3.75MB、v3约5.86MB。这是值得核验的组合方向，但没有查到它们配中文Baker后的可靠音质／手机温耗对照，**不能把原配0.391的RTF移给新组合，也不能从小文件推定低计算**。[官方搭配说明](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html#matcha-icefall-zh-baker-chinese-1-female-speaker)、[声码器发布资产](https://github.com/k2-fsa/sherpa-onnx/releases/tag/vocoder-models)。

小型AISHELL3本轮核到的发布权重仍是8kHz，没有找到同系列已发布的16／22.05kHz轻量中文升级。旧121MB／INT8约39.9MB AISHELL3也仍是8kHz；`fanchen-wnj`虽有明确男声和16kHz，计算证据又不占优。174声的原始名单存在；首次调查未建立可靠SID→性别映射，随后公开样音筛选已补齐两个男声，见第10节。[旧AISHELL3](https://huggingface.co/csukuangfj/vits-zh-aishell3/tree/main)、[小模型speaker名单](https://huggingface.co/csukuangfj/icefall-tts-aishell3-vits-low-2024-04-06/blob/main/data/speakers.txt)。

有一个跨项目的佐证但不能拿来给中文排序：VoicePing 作者公开了 Galaxy S10／四线程的英文测试，Piper低规格模型明显快于所测Kokoro，且INT8 Kokoro更慢；全部模型“PASS”只指未崩溃／OOM，并不表示实时、声音好或低热。它支持优先考察轻量架构，也再次否定“量化必定更快”。[作者测试与代码入口](https://voiceping.net/zh/blog/research-offline-tts-eval/)。

许可只保留影响选择的事实：sherpa／icefall代码和AISHELL3相关来源标Apache-2.0；Matcha Baker及Piper小雅关联数据有非商业限制；Melo代码和原权重标MIT；Kokoro中文权重标Apache-2.0。旧Piper已归档，后继`piper1-gpl`与通过sherpa运行转换模型是不同接入链，不能混称同一许可。当前个人使用不因此增加发行审批流程。[AISHELL3数据](https://www.openslr.org/93/)、[Matcha卡](https://huggingface.co/csukuangfj/matcha-icefall-zh-baker)、[小雅单音色卡](https://huggingface.co/rhasspy/piper-voices/blob/main/zh/zh_CN/xiao_ya/medium/MODEL_CARD)、[Piper后继](https://github.com/OHF-Voice/piper1-gpl)。

## 5. 继续向移动端工程和新模型扩查的结果

### 值得保留的另外三条路线

**Bert-VITS2-MNN：有完整中文Android实现。** 文本处理、蒸馏BERT和BV2均在本机，并已有AAR封装。作者报告骁龙888的一个中英短句：22.05kHz，生成5.20秒声音耗时1.856秒，RTF约0.357；BV2 INT8模块约29.7MB，另有BERT与前端，不能把29.7MB当整包。这提供了直接的Android短句速度线索，但只是一句，不能推广到热态长听。示范声音多为游戏角色，22k底模写明学习交流用途；代码Apache-2.0不能覆盖声音权重的全部条件。先找到听感合适、来源明确的固定声音，才值得替换。[作者工程及条件](https://github.com/Voine/Bert-VITS2-MNN)、[AAR入口](https://github.com/Voine/Bert-VITS2-MNN/blob/master/PUBLISHING.md)。

**FastSpeech2＋轻量声码器：老路线仍有现实价值。** `ChineseTtsTflite`是完整的中文离线Android引擎，作者明确FastSpeech路径较快但拟人较弱，Tacotron则太慢，仅作测试；依赖与中文前端较旧，日期、人名等需重新核对。PaddleSpeech官方也有FastSpeech2＋MB-MelGAN的Android示例，但**该Demo没有完整文本前端，使用预设音素ID**，不能称任意中文已打通。官方CPU比较提示声码器可能决定大部分耗时；应选完整合成组合，不能只选一个很小的声学模型。[ChineseTtsTflite](https://github.com/benjaminwan/ChineseTtsTflite)、[Paddle官方Android示例](https://github.com/PaddlePaddle/PaddleSpeech/blob/develop/demos/TTSAndroid/README.md)、[官方组合测量](https://github.com/PaddlePaddle/PaddleSpeech/wiki/TTS-Benchmark)。

**MOSS-TTS-Nano：若保留克隆能力，值得观察。** 中文、多语、参考声音克隆，约100M主模型加约20M音频codec，两部分模型卡标Apache-2.0；已有官方Android ONNX工程。但示例只提供两条预先token化的输入，任意小说还需本地SentencePiece；默认160帧上限是冒烟验证设置，输出WAV不等于完成听书。公开提速主要来自MacBook Air M4，作者也明确手机热行为因设备而异。不能把“四核CPU可跑”直接翻译成小米低热。[官方项目](https://github.com/OpenMOSS/MOSS-TTS-Nano)、[Android示例与限制](https://github.com/OpenMOSS/MOSS-TTS-Nano/tree/main/examples/android_onnx_runtime)、[主模型](https://huggingface.co/OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX)、[codec](https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX)。

### 查到了，但目前不进入第一批的候选

| 候选／相近项目 | 为什么查，为什么暂缓或排除 |
| --- | --- |
| PocketTTS-ZH-6L | 确有中文社区权重，INT8连Mimi约223MB，标Apache-2.0；速度证据是桌面i7。卡片承认长输入跳词、极短句可能静音或复述参考。已有Pocket Android工程不等于这套中文权重已验证，暂作研究候选。[中文卡](https://huggingface.co/hustfyb/PocketTTS-ZH-6L)、[Android社区实现](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine) |
| Supertonic | 官方当前列出的语言不含中文，仓库已归档；不能拿英语速度当中文方案。另有非官方中文扩展，但权重获取和Android接入仍有缺口。[官方](https://github.com/supertone-oss-archive/supertonic)、[中文扩展](https://github.com/wuxuedaifu/supertonic_cn) |
| NeuTTS Air／Nano／2E | 所核官方语言清单未包含中文，不能因为支持CPU／GGUF／克隆就列为即用普通话方案；与另行商业宣传的产品也不能混称。[官方模型与语言](https://github.com/neuphonic/neutts) |
| Kitten TTS 2 | 新版已支持中文，不能沿用“Kitten只有英文”的旧结论；但新版是1.7B一位模型，最小约506MiB、官方建议约6GB RAM，权重为Stellon社区许可。未核到新版中文Android持续温耗，不能沿用旧Kitten小模型的体积与速度。[官方](https://github.com/KittenML/KittenTTS)、[新版模型](https://huggingface.co/KittenML/kitten-tts-2) |
| Qwen3-TTS | 中文／声音能力强，0.6B／1.7B，已有Android移植；低热长听证据不足，适合作质量参照而非默认省算力替代。[官方](https://github.com/QwenLM/Qwen3-TTS) |
| CosyVoice3-MNN | 真正Android CPU＋OpenCL移植，Flow蒸馏至2步；作者只测一台荣耀Magic8 Pro，模型约1.3GB，暖缓存RTF约0.79～0.96。暖缓存不等于热稳定；若原速合成再1.45×播，这个吞吐仍不足。[作者实现](https://github.com/Nian27/CosyVoice3-MNN) |
| F5-TTS | 中英克隆明确，主模型约335.8M加Vocos、迭代生成；未核得目标手机持续低热证据。代码MIT、预训练权重CC-BY-NC，分别记录。[官方](https://github.com/SWivid/F5-TTS)、[权重](https://huggingface.co/SWivid/F5-TTS) |
| MMS-TTS／Adreno OpenCL移植 | 确有GPU移植线索，但Meta覆盖表中普通话没有对应TTS，不能把客家话或中文ASR支持当普通话合成。未形成可用的普通话候选。[Meta语言表](https://dl.fbaipublicfiles.com/mms/misc/language_coverage_mms.html)、[OpenCL工程](https://github.com/a8nova/adreno-llms/tree/main/src/models/mms-tts) |
| Vits-Android-ncnn | 有中文本地前端，但作者提示Vulkan路径不完整且可能更慢；换框架／GPU标签不自动带来收益。[作者实现](https://github.com/weirdseed/Vits-Android-ncnn) |
| eSpeak NG／Ekho／RHVoice | eSpeak普通话和Android成立，但共振峰声音自然度有限；Ekho历史普通话走采样拼接，当前Android包不能直接确认为普通话；RHVoice当前语言表没有中文。适合了解低计算的代价，不作为自然中文现成胜者。[eSpeak](https://github.com/espeak-ng/espeak-ng)、[Ekho架构](https://www.eguidedog.net/doc/doc_develop_ekho.php)、[RHVoice](https://github.com/RHVoice/RHVoice) |
| EfficientSpeech | 266k仅声学模型；“104倍”是mel生成，完整波形还需声码器。公开权重以英文为主，未核得即用中文，不把学术小模型当可交付引擎。[原实现](https://github.com/roatienza/efficientspeech)、[论文](https://arxiv.org/abs/2305.13905) |
| Sopho古诗TTS | 有约68MB的中文FastSpeech2＋HiFiGAN及Android ONNX验证，但只针对古诗；卡片20首留出诗歌的ASR CER约21%，不能据此称通用小说已读准。[模型卡](https://huggingface.co/Sopho/sopho-poetry-tts) |

一个有警示价值的实机结果：Google AI Edge的Qwen3-TTS **0.6B Base／INT4 talker／参考图FAST=0／CPU XNNPACK**，在Galaxy S26冷却至36°C后，生成2.40秒英文耗12.5秒；此前三次温热运行耗15～29秒。手机音频未采集，也没有测当前默认FAST图，所以它既不是中文音质结果，也不能概括所有Qwen移植。它证明的是“能量化并在Android运行”离持续听书可能仍很远。[原始条件和测量](https://github.com/google-ai-edge/litert-samples/blob/main/models/qwen/qwen3_tts/README.md)。

## 6. 硬件加速值得研究，但不是切一个开关

### ZipVoice 同类应用：CloneTTS

这是与本项目痛点最接近的一条新线索。作者明确列出 ZipVoice、sherpa、ONNX Runtime 和高通 QNN；v0.7.0宣称在骁龙8 Gen2／Gen3／Elite／Elite Gen5上使用NPU，较CPU约快两倍、发热更低。发布记录还涉及翻页首句杂音与快速播停竞态，说明声音异常也可能发生在播放接缝。[项目说明](https://github.com/sipeter/CloneTTS)、[v0.7.0发布](https://github.com/sipeter/CloneTTS/releases/tag/v0.7.0)。

但当前公开仓库只有README和指南，没有可审核、复用的引擎源码；不能列为“已找到可直接搬用的开源ZipVoice NPU实现”。指南列出额外约600～740MB的芯片专用加速包、约10秒冷准备及超长段落CPU回退，也给出降温宣传，却没有完整配对温耗数据。默认步数、参考长度及标准／蒸馏模型与本项目也不相同。它值得后续作为对照或追踪源码，不能据此恢复已撤回的两步／短参考，也不能承诺两台小米直接取得相同收益。[作者指南](https://github.com/sipeter/CloneTTS/blob/master/docs/CloneTTS%20Beginner%27s%20Guide%20%26%20FAQ.md)。

### 有公开实现的 Kokoro QNN 工程

`cedgeremek/kokoro-offline-tts-android`公开了CPU前处理、HTP声码器、静态长度分档、会话复用和边生成边输出的工程路径。它保留整句韵律上下文，再按音频帧切小窗口；这比把每个小片段重新当独立句子合成更值得借鉴。[架构说明](https://github.com/cedgeremek/kokoro-offline-tts-android/blob/main/docs/ARCHITECTURE.md)。

作者报告的实机是 **Galaxy S24 Ultra／SM8650，英文Kokoro v1**。约8分钟会话的生成器平均RTF0.2911，只是生成器口径；整场497.775秒交付460.470秒声音，不能用这个RTF冒充全流程连续性或中文成绩。项目自己明确没有受控温耗实验。[测量说明](https://github.com/cedgeremek/kokoro-offline-tts-android/blob/main/docs/PERFORMANCE.md)、[能耗边界](https://github.com/cedgeremek/kokoro-offline-tts-android#power-minded-design)。

当前包约1.40GiB，公开适配仅覆盖特定芯片；其他芯片需重新编译加速文件并核对厂商访问路径。这不是换回旧Kokoro模型就能得到的能力。对本项目而言，值得学习“把主要计算放到合适硬件＋避免重复初始化＋保留句子上下文”，不适合跳过轻量模型筛选直接大改底层。[兼容矩阵](https://github.com/cedgeremek/kokoro-offline-tts-android/blob/main/docs/COMPATIBILITY.md)、[打包范围](https://github.com/cedgeremek/kokoro-offline-tts-android)。

ONNX Runtime官方QNN说明也明确：需要适配支持的算子、固定动态形状，所述HTP量化路径还要求正确校准。设备支持NPU，不等于任意ONNX模型自动全部在NPU运行；CPU回退和数据搬运必须计入。sherpa已有的QNN语音识别示例也不能充当ZipVoice合成已适配的证据。[QNN官方要求](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)、[sherpa QNN ASR](https://k2-fsa.github.io/sherpa/onnx/android/apk-qnn-asr-streaming-cn.html)。

## 7. 系统与商业离线引擎：解释行业经验，保留对照

| 路线 | 公开事实 | 对当前目标的判断 |
| --- | --- | --- |
| 设备已有离线系统音色 | Android能选无需网络的Voice；实际声音包需已安装，初始化成功不证明一定离线 | 最贴近用户现有App体验的对照；此前项目用小爱时男声选择不足，不能默默改回去。一次枚举和同文对照比猜闭源App算法有用。[官方Voice接口](https://developer.android.com/reference/android/speech/tts/Voice) |
| 阿里离线SDK | 本机合成；官方提供低精度降低计算、低CPU峰值模式但合成变慢；首次联网激活，有试用及商业永久授权 | 工程成熟路线，能学习计算／质量取舍；非开源免费默认方案，CPU峰值低也不等于总能耗低。[参数](https://help.aliyun.com/zh/isi/user-guide/sdk-reference-11)、[授权](https://help.aliyun.com/zh/isi/user-guide/enable-authorization) |
| 讯飞普通／轻量离线SDK | 旧普通版基础资源与单音色是数MB量级；AIkit轻量版本地合成但需首次激活，授权有有效期和缓存边界 | 证明轻量中文端侧并不新鲜；不能从过时搜索摘要得出无限免费。当前SDK、Android16及声音套餐需另核。[旧MSC](https://www.xfyun.cn/doc/tts/offline_tts/Android-SDK.html)、[轻量版](https://www.xfyun.cn/doc/tts/AIkit_offline_tts/Android-SDK%28Lightweight%29.html) |
| 百度离线SDK | 有纯离线模式、ARM64、本地音库；首次取得授权，离线时仍受有效期约束 | 可作为行业路线，未查到目标手机长听温耗证据；不是开源替代。[官方Android说明](https://ai.baidu.com/ai-doc/SPEECH/cltwwjwqm) |

这些资料说明，“声音准备好之后长期在本机读文本”是成熟产品路径，不需要转线上生成。它们没有证明用户举例的任何具体App正在用上述某一家SDK。

## 8. 可以借鉴的实现方向

**把中文前端也算作朗读能力。** 数字、日期、单位、英文夹杂、多音字、人名和引号边界会决定“读得通不通”。存在中文声音模型不等于这些都处理正确。sherpa的Matcha示例已带电话号码／日期／数字规则；本项目也曾因ZipVoice原生路径缺少相应规范化而修复数字读法。替换时应核对实际前端，而不是只试听一句无数字的问候语。[Matcha正式输入示例](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/matcha-icefall-zh-baker.html)、[本项目数字问题](../../knowledge/bugs/BUG-2026-061-zipvoice-chinese-numbers-use-english-front-end.json)。

sherpa v1.13.8的中文词典路线存在词典外字符忽略等分支，`CharacterLexicon`又有短语匹配和字符回退。看到`use_g2pw`标志不能直接声称Android加载了原Python上下文模型。小说比较应覆盖日期金额、章节编号、人名多音字、引号对话和长无标点句。[词典实现](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/lexicon.cc)、[字符前端](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/character-lexicon.cc)。

**考虑原生语速，避免算出马上被快放压缩的全部波形。** sherpa v1.13.8的VITS和Matcha把 `1 / speed` 作为时长缩放输入送给模型，有重新核验原生1.45×的价值；收益及字音完整性仍需实测。当前ZipVoice固定1×是已知截尾问题的规避，不因其他引擎支持原生变速就改回去。[VITS源码](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-tts-vits-model.cc)、[Matcha源码](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-tts-matcha-model.cc)。

**流式输出、合适分段和复用是帮助，不是能量来源。** 更早交付PCM可以减少首声等待；保留句子上下文可能改善衔接；会话复用避免重复加载。它们不能让平均计算慢于播放的模型无限长听。整章缓存会把计算前移，手机先整本合成会把热量集中到准备阶段，均不等同低运算。

例如`ybsoftware/AndroidTTS`确实接了中文VITS和流式AudioTrack，但使用的是旧AISHELL3；生成回调之后才创建播放器。没有支撑其“首音约10ms”的设备、文本和计时记录，`MODE_STREAM`也不能证明模型真正逐帧生成。值得借接线，不采信该速度宣传。[资源脚本](https://github.com/ybsoftware/AndroidTTS/blob/main/download-model.sh)、[实际控制器](https://github.com/ybsoftware/AndroidTTS/blob/main/app/src/main/java/com/aiproject/androidtts/TtsController.java)。

**音频质量与热问题要分别定位。** 后续若进入实验，最有区分力的是同一正文在冷态／温态同时留存原始PCM和实际播放：PCM本身已坏，优先查合成配置／模型／前端；PCM正常而输出坏，查供给、AudioTrack变速和播放交接。即使换了快模型，已有播放缺陷也可能保留。这个判断分支不需要先建监控平台，更不以给声音套降噪模型掩盖失真。

## 9. 下一步怎样投入才划算

本报告只给建议，不把以下步骤描述为已经执行或已经选定产品方案。

1. **先用公开样音和准确资源清单排除不合听感的音源。** 中文Piper和Matcha Baker用于比较较高采样率的固定声音；小型AISHELL3用于回答8kHz是否还能满足长听。Matcha的小声码器组合和Bert-VITS2-MNN接着评估。若男声仍必须保留，先核具体声音而非按名称猜测；女声候选不能冒充满足要求。
2. **只让少数声音合格的候选进入手机比较。** 同一代表正文、同一1.45×目标、相同设备条件，观察热态持续供给和实际音轨。先证明音源值得换，再接回正式应用的分段、预取、定位和控制；最终验收仍必须走正式App流程。不要先安装十几个引擎或完成整套切换界面。
3. **保留更高投入的克隆路线：MOSS-Nano与NPU。** 若轻量固定音色均不合格，或用户仍要求保留克隆声音，再比较新模型与ZipVoice加速的接入成本。MOSS需补任意文本入口，ZipVoice NPU需可审核实现与转换链；不能只看NPU标志，须核CPU回退、冷启动、长段和声音一致性。
4. **系统离线声音只作明确的产品取舍。** 它可能最快得到用户熟悉的体验，但改变独立打包与声音选择边界；商业SDK同理，不把收费或在线激活悄悄带进当前免费离线目标。

值得投入的是一轮有淘汰标准的音源比较，以及必要的播放诊断；目前证据不支持再做一轮全App大重构来解决发热。研究结论是“已有更匹配的技术路线”，尚不是“某模型已经在两台手机上解决问题”。

## 10. 10月21日公开样音初筛

用户选择先听公开中文样音、男声优先，听感可接受后再做手机验证；本阶段不接入App、不安装或运行新引擎。以下均为发布方的合成演示，尚无用户偏好结论，也不等于正式小说与1.45×听书验收。原始音频保留在发布方地址，本轮没有下载、拼接、调速或修饰声音。

| 编号 | 具体声音 | 试听 | 身份与用途 |
| --- | --- | --- | --- |
| A | icefall AISHELL3，SID 21 | [官方WAV，约6.94秒](https://k2-fsa.github.io/sherpa/_static/vits-zh-aishell3/liubei-21.wav) | 男声，SSB0241；8kHz，默认1×。先判断能否接受音色，短句不能代表长篇稳定性 |
| B | icefall AISHELL3，SID 10 | [官方WAV，约3.36秒](https://k2-fsa.github.io/sherpa/_static/vits-zh-aishell3/liliana-10.wav) | 男声，SSB0073；同一轻量模型的另一声音 |
| C | Piper 超文，chaowen-medium | [sherpa同文MP3，约34.20秒](https://k2-fsa.github.io/sherpa/onnx/tts/all/piper/mp3/zh_CN/vits-piper-zh_CN-chaowen-medium/0.mp3) | 22.05kHz；新增固定中文声音候选，原模型卡未标性别，不凭名字确认。与D/E读相同正文 |
| D | Piper 小雅，xiao_ya-medium | [sherpa同文MP3，约35.11秒](https://k2-fsa.github.io/sherpa/onnx/tts/all/piper/mp3/zh_CN/vits-piper-zh_CN-xiao_ya-medium/0.mp3) | 女声，22.05kHz，来源为标贝标准女声语料 |
| E | Matcha icefall Baker | [官方MP3，约33.64秒](https://k2-fsa.github.io/sherpa/onnx/tts/all/matcha/icefall-zh/mp3/0.mp3) | 女声，22.05kHz；页面示例为三步＋Vocos，不能当作小HiFiGAN组合的听感 |
| F | Bert-VITS2-MNN，22娘 | [作者媒体附件](https://github.com/user-attachments/assets/fd6cc25c-79ca-42fc-a60a-352d8f99e437)／[作者样音表](https://github.com/Voine/Bert-VITS2-MNN#-示例音频) | 女角色，22.05kHz，中英混合短句。作者Android工程样例，没有逐段构建与量化身份，不宣称等同当前29.7MB构建 |

补齐首次调查的男声身份缺口：A/B的SID由[有序speaker列表](https://huggingface.co/csukuangfj/icefall-tts-aishell3-vits-low-2024-04-06/blob/main/data/speakers.txt)对应[原始性别表](https://huggingface.co/datasets/AISHELL/AISHELL-3/blob/main/spk-info.txt)。[官方生成命令及播放器](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#aishell3-chinese-multi-speaker-174-speakers)明确使用29MiB icefall；音频虽保留旧目录名，[2024-03-26更新](https://github.com/k2-fsa/sherpa/commit/f01690b236284acc83643eef06017cf8a7ee5ebc)同时替换了模型说明及两份WAV，故不是仅改文字、沿用旧121MB录音。

C/D/E的[超文页面](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/vits-piper-zh_CN-chaowen-medium.html#samples)、[小雅页面](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/vits-piper-zh_CN-xiao_ya-medium.html#samples)、[Matcha页面](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese/matcha-icefall-zh-baker.html#samples)都给出原文与默认speed=1.0；包含多音字、数字和后半段叙述。A–E已在官方页面浏览器播放器读取到有效时长、readyState=4且无媒体错误，这只验证载入，不替代用户核听。

补充可比音色：Piper官方“彩虹”同文样音还有[huayan medium／22.05kHz](https://rhasspy.github.io/piper-samples/samples/zh/zh_CN/huayan/medium/speaker_0.mp3)和[huayan x_low／16kHz](https://rhasspy.github.io/piper-samples/samples/zh/zh_CN/huayan/x_low/speaker_0.mp3)，浏览器载入时长分别19.15／19.54秒。它们是同一音色不同模型档位，原模型卡未确认性别；x_low仅作为新增声音比较，不因名字就认定能耗合格。[Piper官方来源](https://rhasspy.github.io/piper-samples/)、[小雅声源说明](https://www.data-baker.com/open_source.html)。

Bert-VITS2-MNN此次未找到可确认的中文男声现成样音；其余公开中文角色多为44.1kHz，不混入22kHz轻量配置对比。ChineseTtsTflite没有找到与其Android量化组合对应的可交付公开样音，不用其他桌面构建冒充。后续只将用户认为可接受的声音推进到更长的同文及手机验证。
