# 离线声音依赖与资源准备

本页维护当前接入的语音资源、前端契约、固定摘要和准备入口。产品功能与验收结论由[当前状态](../current-state.md)维护；选型和历史样音见[10月21日调查第14、15节](../superpowers/specs/2026-10-21-offline-tts-landscape.md#14-10月22日现成piper六男声同文试听)，替换范围见[10月22日讨论](../superpowers/specs/2026-10-22-piper-listening-replacement.md)。

**2026-10-22状态：Piper资源与App接线已完成，正在进行正式播放验收；资源检查完成不代表完整前端听感或手机温耗已验收。** 本页记录依赖身份，不提前宣称候选交付完成。此前ZipVoice配置和证据保留在本页历史节。

## 当前接入：一份Piper模型、C/F两个声音

使用社区 `writeAPoemForYou/piper-voices-zh-AISHELL-3` 已训练的174说话人拼音模型，共享一份权重，仅开放用户确认的两个声音，C为默认。不训练、不使用参考录音、不加载运行时G2PW，不使用在线语音服务。

| 声音 | 发音人／SID | 持久化标识 |
| --- | --- | --- |
| C · SSB1020，默认 | SSB1020／30 | `piper:aishell3:ssb1020` |
| F · SSB0863 | SSB0863／151 | `piper:aishell3:ssb0863` |

此处C是10月22日六男声试听中的SSB1020，不是此前公开样音编号C的超文。174声模型本身没有变成两声模型；限制的是App可选项。旧值、空值和未知音色由[内置音色表](../../app/src/main/java/com/longerlsx/storyapp/core/model/ReaderBundledVoices.kt)解析到C，不为此修改书籍或进度格式。

| 参数 | 本轮接线配置 |
| --- | --- |
| 推理运行库 | sherpa-onnx Android 1.13.8，CPU，`numThreads=2`；未因换声升级AAR |
| 模型资源目录 | `piper-zh-cn-aishell3-v1`，一份自包含ONNX；不再单独接Vocos |
| 输出采样率 | 22,050Hz、单声道；引擎检查实际返回采样率 |
| 生成参数 | `noiseScale=0.333`、`noiseScaleW=0.333`、`lengthScale=1`；`GenerationConfig.speed=1`、`silenceScale=0.2` |
| 用户速度／音调 | 自然1×合成后，由现有AudioTrack播放端应用语速和独立音调；不叠加Piper原生加速 |
| 前端 | App规范化及覆盖检查 → sherpa `CharacterLexicon`静态词典；不配置原生`ruleFsts`或eSpeak数据目录 |

实际接线由[OfflineReaderTtsEngine](../../app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/OfflineReaderTtsEngine.kt)维护。两个SID共用模型初始化；自然语音缓存、任务身份、取消及原文进度沿用现有链路。两线程是当前候选配置，不是已证明的手机最优线程数；桌面41条词典初筛不能替代完整前端的成本和手机长听验证。

## 中文前端与资源契约

### 词典来源和适配

源词典为sherpa发布的完整 [lexicon-zh-g2pw.txt](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-zh-g2pw.txt)。[官方生成器](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/piper/generate_lexicon_zh.py)取pypinyin的单字／短语集合，经Piper `ChinesePhonemizer` 离线生成读音；单字生成范围为U+4E00至U+9FFF。手机运行时只查预生成词典，`has_g2pw=1`在固定sherpa版本中表示选择该前端，不表示加载G2PW神经网络。[固定前端选择](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-tts-vits-impl.h)

本轮下载的官方词典有68,012条、20,901个单字项；全量检查空项、重复键、未知音素均为0。转换保留全部源词条，另加26个小写字母名称和15个标点项，得到68,053条。原词典已有声调后的`_`，不重复插入PAD；新增字母每个音节同样带PAD，新增标点为“标点＋PAD”。句首`^`和句尾`$`由sherpa加入，不写进词条。

`tokens.txt`从所选模型JSON的`phoneme_id_map`生成，保留全部85个拼写，包括同ID的中英标点别名和空格；不按ID去重。它不能与旧ZipVoice的`y0 i1`等音素表混用。模型仅将元数据改成下列固定值，图与权重未改，也没有重新导出或量化：

```text
model_type=vits  comment=piper  language=Chinese  voice=zh
version=1  has_espeak=0  has_g2pw=1  n_speakers=174  sample_rate=22050
```

### 数字、混排和未命中

[中文前端](../../app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsChineseFrontend.kt)在模型调用前处理合成输入；[数字规范化](../../app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsChineseNumberNormalizer.kt)由App负责，不与原生FST重复转换。数量、日期／时刻、年份、号码和型号等规则属于本轮实现与回归范围，尚不能从资源检查推定已经读对。正文中的数字必须转换为明确读法或进入整段错误路径，不能把`1`至`5`等原始字符交给音素token回退。

ASCII字母先小写、相邻字母加空格，例如`AI → a i`、`CPU → c p u`，再使用显式字母名称词条。[CharacterLexicon加载词典](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/character-lexicon.cc#L264-L315)会强制将词条键转为小写，但不对输入执行同样转换，因此大小写必须在App侧统一。另一个边界是sherpa的[SplitUtf8及MergeCharactersIntoWords](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/text-utils.cc#L316-L399)会把连续ASCII字母合并成整词；仅添加单字母词条仍可能未命中。26个名称是普通话近似，例如A对应`Ø ei 1 _`，F对应`Ø ai 4 _ f u 2 _`；英文单词也逐字母兜底，不承诺自然英语发音。字母名称辨识度仍需实际核听。

`支持码点表`与最终词典同源：`supported-codepoints.txt`仅收录已通过音素／PAD检查的单字符词项，每行一个大写十六进制码点，最少4位、升序，共20,942项。它不从tokens表提取：小写`a`至`z`只因显式字母名称词项进入覆盖表，声调数字与`_^$`均不收录。App规范化后的可读字符必须在此表内；引号、括号等明确列出的排版分隔符另行处理，其余未知可读正文使整段失败，不播放剩余内容后确认整段完成。

这一检查不能省略：[CharacterLexicon](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/character-lexicon.cc)本身有忽略OOV及同名token回退分支。词语未命中而单字可读时仍可逐字朗读；只存在于短语、没有单字词项的字符先保守拒绝，不再实现一套分词器。规范化只改变合成输入，`ReaderTtsSegment`、完成确认和恢复始终保留原文偏移；错误、空音频及过时结果不能自动推进听书位置。这些接缝的App运行验证由本轮实施记录维护。

### 静态词典的能力边界

[PhraseMatcher](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/phrase-matcher.cc)使用最长10字的正向匹配，并非上下文神经多音字预测。成熟短语词典可提供“银行”“重庆”等读法，但不保证所有新词、人名、生僻字和上下文都正确；例如源词典单字“一”为`y i 4 _`，是否需要规则补充应由真实正文判断。覆盖表证明输入具有词典音素，不证明读音正确，也不能检测所有模型漏读。完整词典、26个字母名称及未参与调试正文的实际内容和听感仍须核验。

## 固定源文件与准备入口

输入摘要由[准备脚本](../../scripts/prepare-offline-voice.sh)执行校验；以下是2026-10-22资源身份记录。

| 源文件 | 来源 | 字节数 | SHA256 |
| --- | --- | ---: | --- |
| `sherpa-onnx-1.13.8.aar` | [sherpa官方发布](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar) | 50,129,134 | `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` |
| 原始`model.onnx` | [社区AISHELL-3权重](https://huggingface.co/writeAPoemForYou/piper-voices-zh-AISHELL-3/resolve/main/zh/zh_CN/cn_aishell3/medium/zh_CN-cn_aishell3-medium.onnx) | 77,639,618 | `f46e009dcfcaa0f8c5e6eefe23dabc36296c69123ea22df82d31078ab6e0174f` |
| `model.onnx.json` | [同一模型配置](https://huggingface.co/writeAPoemForYou/piper-voices-zh-AISHELL-3/resolve/main/zh/zh_CN/cn_aishell3/medium/zh_CN-cn_aishell3-medium.onnx.json) | 6,338 | `54ec178cb1aa0aea290e41e639240b60b2a5fc55087d9957a718817d9f8bafd6` |
| `lexicon-zh-g2pw.txt` | [sherpa词典发布](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-zh-g2pw.txt) | 2,064,505 | `003e76f89fec389ec109b557f47cc315a1ac31a99091f0cc9d15949f9024416b` |

资源准备和Gradle构建串行：

```sh
# 复用现有试听模型、词典、运行库和转换依赖，不联网。
scripts/prepare-offline-voice.sh --offline

# 新环境：复用已有缓存，下载缺少的固定资源与隔离转换依赖。
scripts/prepare-offline-voice.sh

# 指定完整下载目录，离线；文件名为model.onnx、model.onnx.json、
# lexicon-zh-g2pw.txt及固定AAR。AAR可复用.local-tts/runtime已有文件。
scripts/prepare-offline-voice.sh /path/to/completed-downloads
```

无参数或`--offline`模式优先复用`.local-tts/audition-models/piper-cn-aishell3/`原模型／配置；词典下载缓存为`.local-tts/downloads/piper-zh-cn-aishell3-v1/`。指定目录模式使用其源文件，不把别的模型悄悄替代进去。错误源SHA直接拒绝，不覆盖下载原件。

[永久转换脚本](../../scripts/prepare_piper_resources.py)检查配置、完整词典和PAD，适配元数据并生成四份资源。宿主Python要求3.9至3.12，可用`PIPER_PYTHON`指定；固定转换依赖为ONNX 1.17.0、protobuf 5.29.5、NumPy 2.0.2，准备在`.local-tts/tools/piper-resources-py<版本>/`，pip缓存也位于`.local-tts`，不修改系统环境。这些工具仅用于开发机准备，不打进APK；离线模式缺少转换工具时明确失败。

所有输出先写入同文件系统的暂存目录，再以完整目录重命名发布；失败时恢复先前模型目录，不发布半套资源。成功后仅移出本脚本管理的旧ZipVoice/Kokoro assets，清理前在`.local-tts/backups/pre-piper-assets-*`保存完整副本。下载原件、试听文件、回退APK、无关assets及用户数据不删除；不得把整个`.local-tts`或应用私有目录当作可清空缓存。

```text
.local-tts/
  runtime/sherpa-onnx-1.13.8.aar
  assets/piper-zh-cn-aishell3-v1/
    model.onnx
    tokens.txt
    lexicon.txt
    supported-codepoints.txt
```

JSON供宿主转换使用，APK所需的是上述四份输出。模型／词典从AssetManager读取，不需要手机首次下载。AAR仍包含四种ABI，App沿用`arm64-v8a`打包范围；未使用的AAR内置引擎代码不因移除ZipVoice资源而自动消失。

### 派生资源摘要与已完成的资源检查

| 打包文件 | 字节数 | SHA256 |
| --- | ---: | --- |
| `model.onnx` | 77,639,647 | `47f10ff81fd351cf36deed68728075b9c3063b01526335df1af62965f117170e` |
| `tokens.txt` | 485 | `e9e6d2838a2676749adbf0376c4cf3c3cd28eb27ed953dcc2adcab8db3c33ba7` |
| `lexicon.txt` | 2,065,011 | `eb1a93f58074af46a9fd9f4ee9afec773e0874676d8141b70fb2a283e8b401ed` |
| `supported-codepoints.txt` | 104,710 | `dc89a733a1a233c26fe1a3a86217c578f4b9dee773198ce2517eb969b123058e` |

2026-10-22最终小写字母词条方案的正式`--offline`准备成功，派生摘要如上。独立读取输出确认：85个token拼写与JSON逐项相符，68,012条官方词典原内容完整保留，覆盖表与最终单字项一致；适配前后ONNX图的序列化字节相同。未知音素以及错误源SHA分别被拒绝，已发布资源保持不变。脚本语法和差异检查通过；这组检查没有运行声学推理、Gradle或设备，不证明App可用、字母可辨认、完整前端低耗或音质改善。

## 历史ZipVoice基线与回退资料

以下是Piper替换前的依赖身份，**不是当前候选包的备用引擎**。实际回退APK、升级往返与用户数据连续性由[当前状态](../current-state.md)及本轮实施记录维护；资源备份可用不等于APK回退路径已验收。

2026-10-16因用户反馈1.930秒短参考经常失真，恢复ZipVoice-Distill INT8／四线程／3步，使用官方`leijun-1`完整6.057秒参考（PCM16、24kHz、单声道），音色`zipvoice:leijun`。对应参考文字为`那还是三十六年前, 一九八七年. 我呢考上了武汉大学的计算机系.`。模型目录为`sherpa-onnx-zipvoice-distill-int8-zh-en-emilia`，encoder／decoder INT8搭配`vocos_24khz.onnx`，自然1×合成、`silenceScale=0.2`、`min_char_in_sentence=30`。三步低于官方所列四步；完整参考恢复不代表正文音质或真机热态问题已解决。

两步E因滋滋声、部分字听不清而撤回；9月23日短参考及三线程候选也已撤回。三步完整参考D曾有一次真机降温结果，但耗电与重复性未通过完整验收。历史选择与限制见[9月23日阶段交接](2026-09-23-offline-tts-thermal-handoff.md)、[发热改善计划](../superpowers/plans/2026-09-23-offline-listening-thermal.md)；更早Kokoro资源和设备记录见[9月20日计划](../superpowers/plans/2026-09-20-offline-listening.md)。

| 历史下载源 | 字节数 | SHA256 |
| --- | ---: | --- |
| [ZipVoice模型包](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2) | 109,162,785 | `77219c8b40f4ee8d73a7f902305ff6c1128ef9b54461c41b4ca6ed890b6c2803` |
| [Vocos模型](https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos_24khz.onnx) | 54,157,409 | `bcb3b970e384161c4d634f0bb9e999ff1c471b34c9bc0b1049a5014065ed3cc0` |

本轮旧资源副本为`.local-tts/backups/pre-piper-assets-WOY3Nl/sherpa-onnx-zipvoice-distill-int8-zh-en-emilia/`；encoder、decoder、tokens、lexicon、完整参考WAV、Vocos六份摘要以及全部eSpeak文件／相对路径合并摘要均与旧准备脚本一致，eSpeak合并摘要为`8291f49f39de7ee67e7a383694a5e2b3559bbb4ae9ed657aa1d9d92ffd8f2a01`。旧固定准备规则可从替换前提交`61f9fc4`的`scripts/prepare-offline-voice.sh`恢复，不能用当前Piper脚本的参数冒充旧ZipVoice准备入口。

旧版本首次初始化会从自身APK的`espeak-ng-data`重建私有依赖。Piper接线仅在新模型成功初始化后清理原专属`offline-voice/zipvoice-distill-int8-v1`目录，保留书籍、设置及进度。10月22日模拟器已完成“旧版→Piper产生新进度→旧版继续使用→Piper”覆盖安装往返：旧版成功重建该目录并播放，再升级的Piper初始化成功后清除它；正文、两类新增进度及非音色设置保留。具体证据与音色回落例外见[实施记录](../superpowers/plans/2026-10-22-piper-listening-replacement.md#6-执行记录)。原ZipVoice下载包未附`LICENSE`文件，不能声称包内已有完整许可文件。

### 保留的历史问题证据

- **ZipVoice原生2×截短：** 同一34字句，`speed=1`生成6.688秒、`speed=2`仅0.309秒。其[导出代码](https://github.com/k2-fsa/ZipVoice/blob/master/zipvoice/bin/onnx_export.py#L119-L128)先将“参考＋目标”的总长除以speed，再由[sherpa固定实现](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-tts-zipvoice-model.cc#L132-L144)减去未缩放参考帧；使用6.057秒参考时与结果吻合。因此9月22日改为自然1×合成后播放调速。四步完整参考当时的2×、短章尾与跨章证据保留在[历史实施记录](../superpowers/plans/2026-09-22-zipvoice-listening-upgrade.md#执行记录)，不能外推为Piper原生加速有相同缺陷。
- **ZipVoice数字前端：** 真实正文“48小时”曾进入英文读法路径；固定[MatchaTtsLexicon](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/matcha-tts-lexicon.cc#L294-L317)将未命中、不含中文的词交给`en-us` eSpeak，ZipVoice实现又不执行`ruleFsts`。当时补入App数字规范化；六项JVM规则及模拟器1×／2×实际朗读的“四十八小时”“二十三点五十九分”证据保留在[BUG-061](bugs/BUG-2026-061-zipvoice-chinese-numbers-use-english-front-end.json)。那版只处理含汉字文本，型号等未知形式保持原样；这是旧实现边界，不是Piper前端目标。

## 来源与许可记录

以下继承既有一手资料与[定向调查](../superpowers/specs/2026-10-21-offline-tts-landscape.md#11-piper定向调查超文现成音色与训练边界)，本次没有扩大许可研究。当前用途为个人自用；来源记录供维护，不改变项目许可证，也不把代码许可概括为全部模型、数据或AAR的许可。

| 依赖 | 来源与声明 |
| --- | --- |
| Piper AISHELL-3声学模型 | [作者模型库](https://huggingface.co/writeAPoemForYou/piper-voices-zh-AISHELL-3)标注Apache-2.0；数据来自[AISHELL-3](https://huggingface.co/datasets/AISHELL/AISHELL-3)，数据集与运行代码的条件分别记录，不能沿用超文众包数据的CC0声明 |
| 静态中文词典 | 上述sherpa发布文件及[生成器](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/piper/generate_lexicon_zh.py)，生成链使用pypinyin字词集合和Piper ChinesePhonemizer；手机不运行生成器或G2PW，不将声学模型的许可代替词典来源记录 |
| OHF Piper | [piper1-gpl](https://github.com/OHF-Voice/piper1-gpl)为GPL分支，既有调查已区分旧MIT运行代码；本项目实际Android推理入口为sherpa，不因使用Piper格式就声称已部署OHF Python运行时 |
| sherpa-onnx 1.13.8 | [代码许可](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE)Apache-2.0；不能概括整份AAR所有依赖 |
| ONNX Runtime 1.28.2 | [代码许可](https://github.com/microsoft/onnxruntime/blob/v1.28.2/LICENSE)MIT，保留该版本ThirdPartyNotices来源 |
| piper-phonemize | 固定提交`f3ff95afc03640bc1399e113e83361192a2fafb4`的[许可](https://github.com/csukuangfj/piper-phonemize/blob/f3ff95afc03640bc1399e113e83361192a2fafb4/LICENSE.md)MIT |
| eSpeak NG | 固定提交`ed530aa113046142eb5115cf2fc9157854d0ffe1`的[许可](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING)GPL-3.0，仍编在沿用的JNI中；`has_espeak=0`不等于从AAR删除这部分代码 |
| 历史ZipVoice／Distill模型与参考 | [项目](https://github.com/k2-fsa/ZipVoice)、[模型卡](https://huggingface.co/k2-fsa/ZipVoice)、[sherpa说明](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)；2026-10-21已纠正：代码Apache-2.0，模型卡正文明确预训练模型因Emilia数据约束采用CC-BY-NC。ONNX与参考来自固定官方包，不据此推定参考声音的所有用途均获授权 |
| 历史Vocos | [原项目](https://github.com/gemelo-ai/vocos)声明MIT，旧资源使用sherpa发布的24kHz ONNX |

固定AAR依赖链来自sherpa v1.13.8的[构建入口](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/CMakeLists.txt)和[eSpeak配置](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake)。未来公开分发仍按实际二进制、模型和数据核对条件，本轮不据此新增个人使用审批流程。
