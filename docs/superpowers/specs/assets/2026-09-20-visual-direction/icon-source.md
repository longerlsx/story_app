# 大脸猫阅读图标素材来源

2026-09-20。本记录说明原宠物、猫脸生成素材与 Android 资源的关系。模拟器桌面圆形小图标已实拍确认猫脸和耳尖完整；其他形状由自适应资源的安全留白适配，没有逐个测试厂商桌面。

## 原宠物与基准图

- 当前选择证据：`/Users/longshengxi/.codex/config.toml:243` 的 `selected-avatar-id = "custom:longpaopao"`，即「龙跑跑」。
- 已安装精灵图：`/Users/longshengxi/.codex/pets/longpaopao/spritesheet.webp`。
- 基准原图：`/Users/longshengxi/Documents/Codex/2026-08-20/k/work/longpaopao-pet-run/references/canonical-base.png`，1201×1310，RGB PNG。
- 仓库保留的[基准原图副本](icon-canonical-source.png)直接复制自上述文件，没有修改像素或重新编码。
- 原图 SHA-256：`dfe49058d5cd35601b98ed8e0cd2a925e53dc7a6f5da20d50b9d33f291a4e1d1`。

宠物工作目录的 `pet_request.json` 与 `imagegen-jobs.json` 均把该文件记录为 `canonical_identity_reference`。原始生成文件为 `/Users/longshengxi/.codex/generated_images/019e059e-7808-7e62-a24a-ffd4d00d3199/ig_0172fa3967514ea40169fd594b9ad0819193eb0316fa6382cd.png`，与基准图的记录哈希一致。

## 本次猫脸素材

主任务使用内置 `image_gen.imagegen`，以基准图为参考，保留灰白大脸、金色眼睛、白鼻梁和口鼻、严肃表情及像素风格，生成仅含猫脸的透明正方形图。该过程是基于参考图的生成编辑，不是逐像素、无重绘的机械裁剪。

完整生成指令见[图标提示词](icon-prompt.txt)。

- 生成图路径：`/Users/longshengxi/.codex/generated_images/01a08f33-d662-7072-896b-da1d43153064/exec-b9c91936-8be0-4625-8151-e3426a996950.png`。
- 尺寸：1254×1254，透明 PNG；主任务已视觉确认，本次接线再次查看。
- 生成图 SHA-256：`80a5402aacc6eaaffd3ab0537c06a6d619c5fa90baea76ad49580197c85b9eb8`。
- 应用素材：[ic_launcher_cat_face.png](../../../../../app/src/main/res/drawable-nodpi/ic_launcher_cat_face.png)，原样复制生成图，没有后续图像处理。

## Android 接线

`@mipmap/ic_launcher` 使用自适应图标：背景为暖纸色 `#F6F2E8`，前景由 Android 的 `inset` 与 `bitmap` drawable 绘制。前景四边各留 10% 内缩，再叠加原图已有透明留白，给耳尖与脸部保留安全空间；已核对[实际桌面截图](icon-launcher.png)。

应用的 `icon` 和 `roundIcon` 均引用该自适应资源，由启动器选择圆形或圆角方形掩模。项目最低 API 为 28，可直接使用 API 26 起支持的自适应图标；不新增低版本位图套件。应用名和书架品牌共用 `@string/app_name`（大脸猫阅读），内部包名、签名、版本与存储格式保持原有身份。
