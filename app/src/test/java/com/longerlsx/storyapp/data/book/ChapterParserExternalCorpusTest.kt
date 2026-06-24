package com.longerlsx.storyapp.data.book

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserExternalCorpusTest {

    @Test
    fun parsesExternalNovelCorpusWithExactOffsets() {
        val corpusRoot = File("/Users/longshengxi/Downloads/小说测试集")
        assumeTrue("External novel corpus is not available on this machine", corpusRoot.isDirectory)
        val files = corpusRoot
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .toList()
        assumeTrue("External novel corpus has no txt files", files.isNotEmpty())

        var checkedPlayersGuide = false
        var checkedXianyuSystem = false
        var checkedGuawang = false
        var checkedAncientConstruction = false
        var checkedDoomsdayPartner = false
        var checkedSpaceCat = false
        var checkedXiuluochang = false
        var checkedHospitalPalace = false
        var checkedBraisedPork = false
        files.forEach { file ->
            val content = file.readText()
            val normalized = TxtNormalizer.normalize(content)
            val result = ChapterParser.parseDetailed(content)
            assertTrue("${file.name} should parse at least one chapter", result.chapters.isNotEmpty())
            assertTrue(
                "${file.name} should preserve most chapter-like headings; diagnostics=${result.diagnostics}",
                result.chapters.size >= (simpleChapterHeadingCount(normalized) * 0.8f).toInt().coerceAtLeast(1),
            )

            var previousEnd = 0
            result.chapters.forEach { chapter ->
                assertTrue("${file.name}: ${chapter.title} start is out of range", chapter.startOffset in 0..normalized.length)
                assertTrue(
                    "${file.name}: ${chapter.title} end is before start or out of range",
                    chapter.endOffset in chapter.startOffset..normalized.length,
                )
                assertTrue(
                    "${file.name}: ${chapter.title} overlaps previous chapter",
                    chapter.startOffset >= previousEnd,
                )
                assertEquals(
                    "${file.name}: ${chapter.title} offsets must point exactly at returned content",
                    chapter.content,
                    normalized.substring(chapter.startOffset, chapter.endOffset),
                )
                previousEnd = chapter.endOffset
            }

            if (file.name.contains("玩家救世指南")) {
                checkedPlayersGuide = true
                val titles = result.chapters.map { it.title }
                assertTrue(
                    "${file.name} should preserve the main 200 chapter headings",
                    result.chapters.size >= 200,
                )
                assertTrue(titles.contains("第2章 独行者2:穿越者？玩家？神？"))
                assertTrue(titles.contains("第84章 幸福玛丽孤儿院1:“我愿意在你的胞宫中沉睡，直到世界的纱幕再一次掀起……”"))
                assertFalse(titles.contains("第十三回 ……"))
            }
            if (file.name.contains("咸鱼和反派错绑对方系统后")) {
                checkedXianyuSystem = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章 001 系统都能绑错？"))
                assertTrue(titles.contains("第51章 050 丹道比试，第一！"))
                assertTrue(titles.contains("第71章 070 他们也跟我一样喜欢她？"))
                assertTrue(titles.contains("第94章 093 有没有可能，是剑龙？"))
                assertTrue(titles.contains("第122章 121 嗯呐么哒。么么。"))
            }
            if (file.name.contains("星际第一种瓜王")) {
                checkedGuawang = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第6章 006 好好吃！"))
                assertTrue(titles.contains("第22章 022 全跑了！"))
                assertTrue(titles.contains("第34章 034 穷鬼退散！"))
                assertTrue(titles.contains("第110章 110 讨好我！"))
                assertTrue(titles.contains("第141章 番外一"))
                assertTrue(titles.contains("第142章 番外二"))
            }
            if (file.name.contains("我在古代搞建设")) {
                checkedAncientConstruction = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第95章 ……"))
                assertTrue(titles.contains("第107章 ……"))
                assertTrue(titles.contains("第140章 ……"))
                assertTrue(titles.contains("第176章 番外被骗了……"))
                assertTrue(titles.contains("第177章 关于嫁儿子……"))
                assertTrue(titles.contains("第178章 关于嫁儿子……"))
            }
            if (file.name.contains("末世反派是我的工作搭子")) {
                checkedDoomsdayPartner = true
                val titles = result.chapters.map { it.title }
                assertTrue(
                    "${file.name} should keep bare numbered chapter headings",
                    result.chapters.size >= 80,
                )
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第80章"))
            }
            if (file.name.contains("小猫咪在星际监狱")) {
                checkedSpaceCat = true
                val titles = result.chapters.map { it.title }
                assertTrue(
                    "${file.name} should keep the main bare-numbered chapter sequence",
                    result.chapters.size >= 60,
                )
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第66章"))
            }
            if (file.name.contains("这该死的修罗场")) {
                checkedXiuluochang = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第59章"))
                assertTrue(titles.contains("第65章 黄金城之战（上）"))
                assertTrue(titles.contains("第66章 黄金城之战（中）"))
                assertTrue(titles.contains("第67章 黄金城之战（下）"))
                assertTrue(titles.contains("第77章 大结局"))
            }
            if (file.name.contains("这座仙宫叫医院")) {
                checkedHospitalPalace = true
                val titles = result.chapters.map { it.title }
                assertTrue(
                    "${file.name} should keep the long bare-numbered chapter sequence",
                    result.chapters.size >= 120,
                )
                assertTrue(titles.contains("第1章"))
                assertTrue(titles.contains("第120章"))
            }
            if (file.name.contains("半夜想吃前任做的红烧肉怎么办")) {
                checkedBraisedPork = true
                val titles = result.chapters.map { it.title }
                assertTrue(titles.contains("第1章 第一块红烧肉"))
                assertTrue(titles.contains("第31章 吃……第一口"))
                assertTrue(titles.contains("第60章 吃……第三十口"))
                assertTrue(titles.contains("第68章 正文完"))
            }
        }

        assertTrue("External corpus should include 玩家救世指南 target file", checkedPlayersGuide)
        assertTrue("External corpus should include 咸鱼和反派错绑对方系统后 target file", checkedXianyuSystem)
        assertTrue("External corpus should include 星际第一种瓜王 target file", checkedGuawang)
        assertTrue("External corpus should include 我在古代搞建设 target file", checkedAncientConstruction)
        assertTrue("External corpus should include 末世反派是我的工作搭子 target file", checkedDoomsdayPartner)
        assertTrue("External corpus should include 小猫咪在星际监狱 target file", checkedSpaceCat)
        assertTrue("External corpus should include 这该死的修罗场 target file", checkedXiuluochang)
        assertTrue("External corpus should include 这座仙宫叫医院 target file", checkedHospitalPalace)
        assertTrue("External corpus should include 半夜想吃前任做的红烧肉怎么办 target file", checkedBraisedPork)
    }

    private fun simpleChapterHeadingCount(content: String): Int {
        return content
            .lineSequence()
            .count { line ->
                SimpleChapterHeadingRegex.matches(line.trim())
            }
    }

    private companion object {
        val SimpleChapterHeadingRegex = Regex("^第[ 　]*[0-9一二三四五六七八九十百千万零〇两]+[ 　]*章(\\s+.*)?$")
    }
}
