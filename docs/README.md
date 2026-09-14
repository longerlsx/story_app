# 项目文档入口

先了解当前需求、功能和实际调用路径；历史计划用于解释过去的决定，不自动变成今天的任务。当前讨论和工作以用户最新要求为准。

## 按任务阅读

| 需要做什么 | 先读哪里 |
| --- | --- |
| 捡起项目、了解功能/交互/代码结构 | [current-state.md](current-state.md) |
| 讨论需求或后续方向 | [current-state.md](current-state.md) 中的需求、能力边界和建议；区分已确认与待选择 |
| 修改代码或安排较复杂的工作 | [协作约定](knowledge/agent-operating-agreements.md)，再读当前状态中相关入口 |
| 诊断已知症状或修 bug | [bug 索引](knowledge/bugs/INDEX.md) 中匹配记录，再核对当前代码和直接证据 |
| 导入/标题/章节切分 | 当前状态中的解析说明 → `ImportCoordinator` / `ChapterParser` 及其测试 |
| 翻页/滚动/目录/设置 | 当前状态中的交互表 → `ReaderScreen` 及对应组件、定位和分页逻辑 |
| 听书/音色 | 当前状态中的男声需求 → `ReaderTtsService` / `AndroidReaderTtsEngine` |
| 构建、设备、语料与验证工具 | [项目 README](../README.md)、[环境与验证操作说明](knowledge/gradle-android-environment-troubleshooting.md) |
| 查询历史决定 | [开发历史](knowledge/2026-04-android-reader-development-cycle.md)，需要时再读 `superpowers/specs` / `superpowers/plans` |

简单问答不要求加载全部项目文档。继续同一任务、上下文压缩或出现新证据时，只刷新缺失或受影响的内容，不重复读全套历史。

## 事实与文档职责

发生冲突时，先看当前代码、实际入口、可重复的运行/测试证据，再看当前状态和稳定操作说明；旧计划、讨论和历史通过记录不能覆盖当前事实。测试必须注明证明范围：模拟引擎不证明真机音色，Activity 重建不证明进程重建，文档检查不证明 APK 可以运行。

| 文档 | 维护内容 |
| --- | --- |
| 本页 | 阅读路由和文档职责，不复制完整功能表 |
| [current-state.md](current-state.md) | 唯一的当前项目概览：需求、功能/交互、实现边界、开放问题及明确标注的建议 |
| [协作约定](knowledge/agent-operating-agreements.md) | 跨任务稳定的工作规则，不存临时优先级和历史任务目标 |
| [环境操作说明](knowledge/gradle-android-environment-troubleshooting.md) | 会反复使用的构建、设备、语料路径与故障处理经验 |
| [bug JSON 与索引](knowledge/bugs/INDEX.md) | 单个已确认问题或有价值调查的症状、原因、状态、证据与修复；索引只负责检索 |
| `superpowers/specs` / `superpowers/plans`、开发历史 | 方案、取舍及本轮执行证据；完成后保留历史身份，项目最新结论由当前状态页汇总 |

同一事实只维护一个权威位置，其他文档链接过去。变更功能时更新当前状态受影响部分；修复已确认问题时更新匹配的 bug 记录和索引。普通项目了解、讨论或文档整理不要求为了流程新增 bug。

## 讨论与记录方式

- 先写本轮对象、代码/资料基线、用户已确认需求；再写事实、推断和待讨论选择，避免把建议写成已经批准的功能。
- 小讨论继续写入当前概览对应部分。只有内容明显需要独立阅读时才新增讨论稿，并从本页或概览链接；结论确定后更新当前状态，讨论稿保留历史身份。
- 证据只保留能解释结论的命令、输入/结果摘要和限制。临时脚本、完整日志、编译产物不进入文档目录；单次诊断文件放临时目录，用完清理。
- 完成前检查整体一致性、链接、重复事实、过期状态和意外文件。文档变更只做文档所需检查，不触发产品全量测试。

本次治理更新借鉴了 [new_ime 文档入口](/Users/longshengxi/proj/new_ime/docs/README.md) 的按任务路由、单一事实来源、当前与历史分离、增量验证原则。2026-09-11 第一阶段实施起，正式采用适合本项目的一次性验收代码预算、验证准入与委派边界，唯一规则源见 [协作约定](knowledge/agent-operating-agreements.md)。new_ime 的业务规则及多仓库发布流程不适用于本项目。
