# 中文男声自然度：上游修复与替代模型调查

日期：2026-09-21。性质：讨论稿／资料调查，不是已批准的替换方案。项目当前音源和固定资源以[依赖说明](../../knowledge/offline-voice-dependencies.md)为准。

## 本轮问题与结论

用户补充：主要试听的是官方资源页面，感到男声断句、口音和语感不自然；App里似乎也有。用户最初要求先查上游处理办法和重视中文自然度的其他模型，随后明确授权ZipVoice-Distill与VITS的真实小说同文试听，结果见[本地试听](#真实小说同文试听)。免费、离线、中文男声、优先随App打包的约束继续有效。

后续用户已明确将筛选顺序调整为端侧性能优先、自然度其次，现有Kokoro保底，权威需求见[当前选型优先级](../../current-state.md#当前验证结果与证据边界)。下文保留调查事实和讨论经过；早先按自然度列出的候选及试听建议，不代表当前实施顺序或性能排名。最新建议见[候选处置](#性能优先后的候选处置建议)。

资料调查查到具体标点修复、词典纠音和完整中文前端移植路线，但没有找到“给现用sherpa 1.13.8升级或调一个参数，即可普遍解决中文语感”的证据。替代候选确实存在，而且已有官方或社区手机入口；候选手机持续听小说、2×生成余量和用户偏好仍未验证。后续仅下载两种候选并在电脑生成试听，未改动产品代码或手机安装。

```mermaid
flowchart LR
    T[原文] --> F[中文处理：词语读音、标点、变调]
    F --> M[模型与音色：声线、重音、语调、时长]
    M --> A[生成音频]
    A --> P[应用播放：衔接、倍速、中断与恢复]
```

这是理解问题的分层，不是所有模型都使用独立中文前端。官方样音也有相似听感，足以说明不能只归因于App的切句或播放队列；尚不足以区分前端与模型各自的影响。读错词、停顿位置不自然、停顿太长、生成跟不上是不同问题，不能用缩短静音或增加音色数量统一代替。

## 方向一：当前Kokoro／sherpa路径已有的办法

| 线索与状态 | 具体做了什么 | 对当前项目的判断 |
| --- | --- | --- |
| 标点补丁已合并：[#2146](https://github.com/k2-fsa/sherpa-onnx/pull/2146)（2025-04-24）、[#2458](https://github.com/k2-fsa/sherpa-onnx/pull/2458)（08-07）、[#2522](https://github.com/k2-fsa/sherpa-onnx/pull/2522)（08-25） | 分别处理短标点片段合并、过早转小写干扰、独立标点token | 相关逻辑已在1.13.8中，不是待打的新补丁。证明这类问题有人修过，不证明今日语感已解决 |
| [#2662](https://github.com/k2-fsa/sherpa-onnx/pull/2662)（2025-10-09合并）及[1.13.8中文前端](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/kokoro-multi-lang-lexicon.cc) | 移除Kokoro运行时jieba；按短语词典匹配，未命中再落到单字 | `dictDir`已忽略，不能将App没传它判断为配置错误。可通过词条定点纠正姓名、多音词，但不能承诺整体韵律改善 |
| [#1866讨论](https://github.com/k2-fsa/sherpa-onnx/issues/1866#issuecomment-2661457739)及[静音处理源码](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-tts.cc) | 报告者称检测阈值由0.6秒改0.2秒有效；另有`silence_scale` | 1.13.8已经使用0.2秒。静音缩放只改变检测到的静音时长，不理解哪里应停顿，也不纠正口音 |
| 完整Misaki中文处理的移植路线：[RapidSpeech.cpp说明](https://github.com/RapidAI/RapidSpeech.cpp/blob/main/docs/kokoro.md) | 社区实现中文分词、发音规则等处理，目标与原生Misaki路径对齐 | 值得保留的实质改进线索；是另一套运行环境，尚未证明可直接接入当前Android App或解决用户听感 |

需要准确描述前端差异：sherpa的[词典生成脚本](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/kokoro/v1.1-zh/generate_lexicon_zh.py)已使用Misaki v1.1变调、儿化规则，但移除动态词性分词并以统一词性生成固定词条；[原生Misaki](https://github.com/hexgrad/misaki/blob/main/misaki/zh_frontend.py)在输入时处理分词、词性及规则。不是“sherpa完全没有变调”，而是静态词典与动态处理存在差别。上游生成脚本的词条也不能自动当作现有安装资源已包含的词条。

尚有[#2726](https://github.com/k2-fsa/sherpa-onnx/issues/2726)报告句号被读成“dot”，截至调查时仍开放，只有自动建议，未找到维护者确认修复。旧[KokoroSharp #5](https://github.com/Lyrcaxis/KokoroSharp/issues/5)对比不同接入方式的口音投诉，早于当前中文模型版本；关闭状态本身不证明当前问题已修。不能据这些线索全删标点或直接认定模型无可挽救。

## 方向二：明确重视语感的候选

下表的质量描述是作者公开主张与提供的能力，不是本轮主观试听结论。中文方言支持代表可控范围，不自动证明普通话更标准；内容错误率也不等同于自然断句。

| 候选 | 与用户需求直接相关的能力／试听入口 | 离线Android现状与边界 |
| --- | --- | --- |
| **Fun-CosyVoice3-0.5B-2512** | [官方](https://github.com/QwenAudio/CosyVoice)突出韵律自然度、中文口音及拼音纠音；[示例](https://github.com/QwenAudio/CosyVoice/blob/main/example.py)直接指定“给予”的读音。男声通常从参考音频取得。[官方试听](https://funaudiollm.github.io/cosyvoice3/) | [社区CosyVoice3-MNN](https://github.com/Nian27/CosyVoice3-MNN)有APK和单机实测，自述仍属实验：约1.4GB模型存储、2.25GB常驻内存，热态生成5秒语音大约需4–5秒，长句及连续使用仍有未完成项；还做过模型蒸馏，不能等同官方样音配置 |
| **Qwen3-TTS** | [官方](https://github.com/QwenLM/Qwen3-TTS)强调结合文本语义调整节奏、语调和情感；CustomVoice提供中文男声`Uncle_Fu`，另有明确带北京／四川口音的声音。[官方交互试听](https://huggingface.co/spaces/Qwen/Qwen3-TTS) | [MNN #4589](https://github.com/alibaba/MNN/pull/4589)于2026-08-12合入0.6B-Base支持，属于移动推理线索；[当前接口](https://github.com/alibaba/MNN/blob/master/transformers/README.md)必需参考音频，仅取声音特征，不能直接等同CustomVoice预置男声或1.7B指令能力。本轮未找到可靠手机长听速度证据 |
| **VoxCPM2** | [官方](https://github.com/OpenBMB/VoxCPM)突出按文本语境生成韵律，可文字描述男声及说话方式，也可克隆。[样例页](https://openbmb.github.io/voxcpm2-demopage/)有男声及电台讲故事题材 | [官方MiniCPM-V-Apps](https://github.com/OpenBMB/MiniCPM-V-Apps)已提供离线Android TTS入口；当前2B手机版资源约2.8GB，建议设备内存≥6GB。[下载说明](https://github.com/OpenBMB/MiniCPM-V-Apps/blob/main/DOWNLOAD.md)明确是研究／预览，未优化为成熟产品；不能由GPU实时数据推算手机速度 |
| **ZipVoice／ZipVoice-Distill** | [作者](https://github.com/k2-fsa/ZipVoice)强调自然度和速度，中英支持；使用参考音频及对应文字取得目标声音。[官方样例](https://zipvoice.github.io/) | [sherpa已接入](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)，保留同框架有利；[Android贡献者报告](https://github.com/k2-fsa/sherpa-onnx/issues/3439)Pixel10Pro CPU生成约5秒音频需约5秒，属单人报告且不能证明中文或2×持续余量 |

ZipVoice的已知限制：作者说明仍会发生多音字错误、短文本漏读及偶发长静音，提供拼音指定、降速和去长静音办法；Python入口的这些选项不自动等于当前Android接口全部支持。官方试听配置是普通版16步／蒸馏版8步，也不能直接代表低步数或量化手机配置。这些限制说明需要验证什么，不构成它比其他候选更差的比较证据。

**同日讨论修正：ZipVoice应列入研究候选，不能仅列作对照。** 当前使用的是“sherpa运行框架＋Kokoro模型”，ZipVoice可作为同一框架中的另一个模型接入；共用框架降低适配成本，不代表两者的声线、发音、韵律或速度相同。原建议对ZipVoice公开的问题与一条手机速度报告赋予了过高的排除权重，却没有其他候选在同条件下更好的证据。当时建议优先了解样音；后续用户明确性能第一，因此同框架优势只用于可行方案间的成本比较，不越过端侧性能筛选。

Qwen的[论文长文本测试](https://arxiv.org/html/2601.15621v1#S4.SS2.SSS6)比仅列演示更有参考价值，但衡量内容一致性，且须区分已开放12Hz模型与其他实验配置；不能把论文最佳数字当成候选手机版效果。CosyVoice试听链接由官方提供，资料调查时页面抓取失败，未逐个核对样音标签。该候选表基于公开资料，不是本轮主观听感排名。

## 千问的可靠性与接入边界

进一步核对后的判断：Qwen3-TTS有正式开放的代码、权重和长文本研究，是中文自然度的候选；但尚不是已证明能在15 Ultra／17 Pro独立长听的即用方案。官方CustomVoice的0.6B和1.7B都有预置中文男声，1.7B另明确支持语气指令；Base用于参考音色克隆，不能混为一个能力完全相同的包。官方CustomVoice男声`Uncle_Fu`可用于了解听感，但不因此优先实施，也未确定最终手机版配置。

- 本地部署有新证据：[llama.cpp主线TTS文档](https://github.com/ggml-org/llama.cpp/blob/master/tools/tts/README.md)已提供Qwen3-TTS-12Hz-1.7B-Base-GGUF、中文及参考声音调用。此前MNN 0.6B-Base不是唯一入口，但两者均不自动等同CustomVoice，通用GPU参数也不证明手机TTS性能。
- [MNN #4822](https://github.com/alibaba/MNN/issues/4822#issuecomment-5660794082)的CPU过慢／OpenCL问题帖在2026-09-14关闭，最新评论称master已解决。没有修复后的目标手机端到端数据，不能继续引用旧问题断言GPU不可用，也不能反向声明手机速度已达标。
- [9月15日预印本](https://arxiv.org/abs/2609.16989)仍在研究Qwen3-TTS等模型的超长输入稳定性，提出推理纠错方法。本轮只确认这一研究线索，未完整核对其语言、配置与手机适用范围，不采用其中数字为中文小说验收依据，也不预先引入该算法。
- 开放权重可以本地使用；[阿里云托管API](https://help.aliyun.com/zh/model-studio/tts-model)则是另一种联网、按服务计费的产品，云端版本和音色不能当作可原样打包的开源模型。电脑预生成后传手机属于离线播放已有音频，不改变当前独立离线生成新正文的已定约束。

同日补查MNN实际使用反馈：0.6B已有实现和作者运行报告，参数量本身不足以否决手机路线；能生成、能连续追上播放、长听听感满意仍是三个不同结论。

| 原始反馈 | 实际结果 | 不能据此推出 |
| --- | --- | --- |
| [PR #4589用户复测](https://github.com/alibaba/MNN/pull/4589#issuecomment-4888814846)，2026-07-06 | 0.6B-Base INT8＋参考音频跑通中文合成；最大帧数从64增至128后句子完整，仍称生成慢，没有具体耗时 | 日志中的ARM能力和Linux路径不能证明是Android；没有普通话口音或小说自然断句评价。此前作者[参考音频修正](https://github.com/alibaba/MNN/pull/4589#issuecomment-4885363999)针对噪声故障，也不是整体韵律改进 |
| [MNN #4822原帖](https://github.com/alibaba/MNN/issues/4822)，2026-09-01 | 报告者用0.6B-Base INT4、12核ARM（4×A520＋8×A720），称生成10秒声音约需160秒；未给整机型号。9月14日回复称master已解决并关闭，但未关联提交、解释修复范围或提供复测数据 | 不能确定声明针对CPU性能、OpenCL还是两者；旧耗时不能代表当前实现。该CPU配置不是两款目标小米的实测，也没有修复后持续播放结果 |
| 社区作者的[独立MNN实现](https://pypi.org/project/qwen3-tts-mnn/0.3.1/)及[模型卡](https://huggingface.co/yunfengwang/Qwen3-TTS-12Hz-0.6B-Base-MNN) | Apple M5 Pro、6线程，原生C++路径报告RTF 0.75–0.95，即生成10秒音频约需7.5–9.5秒；纯pymnn路径1.4–1.7。FP16完整资源约1.9GB。使用session池、原生计算及声码器FP16等优化 | 不是官方MNN demo的同条件比较，也不是Android数据；macOS专用Accelerate优化不能原样外推到小米。codec高一致率不是中文韵律满意度 |

本轮检索未找到明确记录15 Ultra／17 Pro型号、修复后MNN版本、持续中文听书速度和听感的报告。网上MNN运行Qwen文字聊天模型的tokens/s、通用OpenCL／QNN支持均不能填补这一缺口。上述1.9GB是社区FP16资源体积，不是手机常驻内存或所有量化版本的大小；TTS除主模型外还有声学码预测与波形解码，不能仅按参数量估算持续速度。若将正常语速音频按2×播放，10秒音频只留下5秒供下一段生成；预缓存只能吸收短期波动，不能弥补长期生成不足。现阶段0.6B是较合理的先行候选，不据这些资料承诺1.7B或目标手机已适用。

## 小米本地加速与Triton的区别

用户进一步询问15 Ultra、17 Pro的GPU方案。两代骁龙平台的Adreno具备OpenCL／Vulkan能力（[8 Elite](https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-elite-mobile-platform)、[8 Elite Gen 5规格](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/images/company/news-media/media-center/press-kits/snapdragon-summit-2025-press-kit/day-2-/documents/Snapdragon8EliteGen5_ProductBrief.pdf)），不能将手机TTS视为只能用CPU；但硬件接口不等于某个模型已经完成适配。

ZipVoice的[Triton部署](https://github.com/k2-fsa/ZipVoice/blob/master/runtime/nvidia_triton/README.md)指NVIDIA Triton Inference Server／PyTriton配合TensorRT，提供Docker和HTTP／gRPC服务，其性能表来自NVIDIA L20。它不是两款小米的本地GPU路径；手机远程调用该服务也不满足当前独立离线要求。

本地技术路线包括[MNN的OpenCL／Vulkan后端](https://github.com/alibaba/MNN)及[ONNX Runtime QNN的GPU后端](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)。QNN的HTP后端则使用NPU，与GPU不同。它们支持相应硬件，不证明ZipVoice能直接换一个配置就受益；仍涉及模型算子、输入长度、运行库及音频正确性。[CosyVoice3-MNN](https://github.com/Nian27/CosyVoice3-MNN)的贡献者报告已用GPU处理Flow部分，却因另一部分在GPU更慢而保留CPU，可作为混合执行的实物线索，不能推广成ZipVoice或两款小米的验收结果。

当前只有CPU手机报告不能据此淘汰ZipVoice，也不能拿服务端加速数字承诺手机收益。GPU／NPU主要改变生成速度和资源消耗，不直接纠正口音或断句；更大模型或更高质量设置是否因此可用，需要后续具体证据。本轮未做GPU适配或手机测试。

本地接线核对：`OfflineReaderTtsEngine`未指定provider，沿用1.13.8的CPU默认值。该版[session实现](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/session.cc#L365)有条件编译的NNAPI入口，但[官方ARM64构建](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/build-android-arm64-v8a.sh#L150)默认API21，启用该分支需要API27；设备系统新不改变已编译条件。NNAPI也不保证落到GPU。当前通用session无QNN分支，sherpa文档中的现成[QNN NPU模型](https://k2-fsa.github.io/sherpa/onnx/qnn/index.html)属于语音识别，不是现成ZipVoice GPU支持。因此同框架的CPU接入优势不能直接推广到GPU，可能需要扩展运行库或转换模型。

## 性能优先后的候选处置（建议）

这是依据当前资料提出的最小筛选顺序，不是已测性能排名或已批准的接入方案。现有Kokoro按用户要求保底；替代方案先证明目标手机持续生成可用，再比较听感，避免花大量工作把慢模型接进完整App后才发现跟不上播放。

| 位置 | 候选与具体配置 | 理由及停止边界 |
| --- | --- | --- |
| 第一轮主候选 | **ZipVoice-Distill INT8，优先核对4步路径** | 已有同框架Android入口、中文男声参考资源和明确少步配置，适合以小样本较低成本确定是否有速度余量。现有Pixel报告只有RTF≈1，不证明中文或2×；没有余量时不直接进入完整接入或无上限优化 |
| 第一轮附带的轻量对照 | **现成固定中文男声VITS：fanchen-wnj** | [sherpa官方](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#csukuangfj-vits-zh-hf-fanchen-wnj-chinese-1-male)明确男声，模型约116MB、16kHz，已有Android入口；[Pi4四线程RTF 1.608](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/rtf.html)提供CPU筛选线索，不推算小米速度。只试一个现成配置，不训练新声音；更轻不等于比Kokoro好听，听感无收益则结束 |
| 第二顺位实验候选 | **CosyVoice3-MNN现成移动优化版** | 有具体旗舰手机和可查实现，但热态整体RTF仍约0.79–0.96；已有两步蒸馏，不把它再算成未来额外提速。资源及持续余量不支持直接承诺2×替换 |
| 暂后置 | **Qwen3-TTS 0.6B＋MNN** | 已跑通，但缺修复后目标手机持续速度；电脑数据不足以支持优先端侧接入。1.7B更不作为当前起点 |
| 暂后置 | **VoxCPM2 Android** | 官方APK与默认男声入口存在，但约2.8GB资源，未找到具体手机完整TTS速度和长听证据，不因官方移动入口自动前移 |

ZipVoice补充证据：[官方](https://github.com/k2-fsa/ZipVoice#32-speed-optimization)建议性能优先使用Distill，可将默认8步降至4步，并采用INT8及较短参考声音；[sherpa中文男声示例](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)已有4步配置。[Pixel案例作者5月22日后续](https://github.com/k2-fsa/sherpa-onnx/issues/3439#issuecomment-4517503648)称日常改用服务器GPU，本地CPU仅作断网回退，不能把其“近乎即时”描述当端侧成绩。另查到[CloneTTS v0.7.0](https://github.com/sipeter/CloneTTS/releases/tag/v0.7.0)发布骁龙8 Elite／8 Elite Gen 5等NPU路径，作者称约为CPU两倍、发热较低；目前公开仓库主要是说明，缺绝对RTF及可直接复用的实现。这是需核实的加速线索，既不能当作当前sherpa包已有支持，也不能把其“快两倍”与另一设备RTF直接相乘。

CosyVoice补充证据：[当前README](https://github.com/Nian27/CosyVoice3-MNN)在HiFT FP16优化后仍列整体RTF约0.82；子阶段约3.2倍加速不能冒充整条链路收益。[研究记录](https://github.com/Nian27/CosyVoice3-MNN/blob/main/docs/RESEARCH_MEMORY.md)曾出现冷机0.63、负载复测1.29，作者撤回其可持续结论；这是历史条件风险，不能写成当前必然退化。明确未完成连续使用验收，优先级因此低于小成本筛选路径。VoxCPM2的[官方发布记录](https://github.com/OpenBMB/MiniCPM-V-Apps/releases)提供男声和调步入口，但本轮未找到补齐手机持续吞吐的报告。

轻量方向也作了排除核对：[MeloTTS社区手机报告](https://github.com/mmaudet/piper-rn-poc#non-piper-voices-nl--ja--zh--model-size-penalty)使用中文女声，吞吐约1.7倍（该文RTF定义为音频时长÷生成时间，与本文相反）、冷首声约11秒，不能当成已满足中文男声和2×需求。[官方Supertonic3](https://github.com/supertone-oss-archive/supertonic#-supported-languages-31)不含中文；[社区中文扩展](https://github.com/wuxuedaifu/supertonic_cn)需申请权重且主要报告电脑CPU／A100数据，未核实男声与手机表现，暂不加入第一轮。AISHELL3虽有更低CPU耗时，但当前公开模型为8kHz，不以牺牲明显声音带宽作为默认升级方向。没有为速度而启动声音训练或扩大成全模型评测。

## 真实小说同文试听

2026-09-21按用户新增授权完成两段电脑本地试听，统一目录为[Downloads试听目录](/Users/longshengxi/Downloads/StoryApp-TTS试听-2026-09-21)，说明及复现方式见其[README](/Users/longshengxi/Downloads/StoryApp-TTS试听-2026-09-21/README.md)。取用户语料《这座仙宫叫医院！》第1章连续336字符，仅去缩进与空行；正文未上传、未进入docs。

- ZipVoice-Distill INT8／4步，官方`leijun-1`男声参考：60.1秒；明确为合成小说朗读，非原讲话录音。包内另外两段参考为女声，本次不凑第二个男声。
- VITS fanchen-wnj固定中文男声／SID 0：56.1秒；该模型只有一个音色。
- 两者均为sherpa-onnx 1.13.8、macOS ARM64 CPU／2线程、1×、默认silence_scale=0.2，无后期变速、变调、降噪、音量归一化或剪静音。已确认非空有限音频、非静音、峰值不越界及完整WAV可解码；未作ASR或主观听感验收。
- 生成脚本73行，模型、依赖、摘录、参数、音频和运行记录均按用户要求保留在上述目录，便于整体回收；重复下载压缩包已删除。单次电脑耗时只存试听说明，不当作手机性能、持续2×或长听验收。

2026-09-22用户反馈：认可ZipVoice的雷军参考声音，希望下一版替换当前全部音色，先只留这一种。昨天使用的是参考录音＋对应文字的零样本合成，没有训练。后续交互与选型边界转到[9月22日讨论稿](2026-09-22-listening-controls-and-voice.md)；本节仍保留原试听条件。

## 讨论建议与停止边界（9月21日调查结论）

- 依照用户最新优先级，先查具体Android实现和持续生成证据，再比较可行方案的中文自然度；手机短句跑通、电脑实时和服务器速度分别标明范围。不预设只有大模型才能改善听感，也不按模型名气、参数量或“实时”宣传排序。
- 现有Kokoro作为已接入的性能基线；若研究完整中文前端，需同时考虑其开销与收益。已包含的旧补丁、单纯缩短静音或增加SID不等于语感改进，改进幅度尚无证据。
- 已调查的ZipVoice、CosyVoice3、Qwen3-TTS、VoxCPM2继续保留，但不是已通过性能筛选的清单，也不限定后续只能从这四者选择。同框架接入成本和官方样音只作辅助信息；性能证据不足不等于判定不可能。
- 若进入下一轮实际评估，必须记录具体模型、前端、音色、精度和步数；手机分支与官方演示不是同一配置时，单独看听感和持续生成能力。2×是现有使用能力，不能只以1×勉强实时宣布替代成功。
- 需要参考音频的方案不代表用户必须自己录音或联网：技术上可预置固定男声参考资源，但来源、选择和实际效果属于后续具体方案。

资料调查和用户指定的两种候选电脑试听已完成；9月22日已收到上述听感选择。尚未启动手机性能测试、切句重构或引擎替换；用户本轮要求先讨论音色并展示界面，不因试听或设计自动重新开启实施目标。
