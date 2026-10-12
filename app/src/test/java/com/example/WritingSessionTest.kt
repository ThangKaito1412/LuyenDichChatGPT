package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.AppScreen
import com.example.viewmodel.TranslationViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WritingSessionTest {
    @Test fun writingReturnsToTheSameSessionAtTheNewPosition() {
        val vm = TranslationViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.targetLanguage.value = "zh"
        vm.isShuffle.value = false
        vm.rawTextContent.value = "xin chào = 你好 (nǐ hǎo)\nthành công = 成功 (chénggōng)\ncảm ơn = 谢谢 (xièxie)"
        assertTrue(vm.startPractice())
        vm.jumpToItem(1)
        val order = vm.practicePairs.value.toList()
        vm.openWriting()
        assertEquals("成功", vm.writingWord.value)
        vm.syncWritingPosition(order[2].vi, order[2].foreign)
        vm.closeWriting()
        assertEquals(AppScreen.Practice, vm.currentScreen.value)
        assertEquals(2, vm.currentIndex.value)
        assertEquals(order, vm.practicePairs.value)
    }
    @Test fun quickWritingCreatesAnOrderedSessionFromTheCurrentInput() {
        val vm = TranslationViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.targetLanguage.value = "zh"
        vm.isShuffle.value = true
        vm.rawTextContent.value = (1..20).joinToString("\n") { "nghĩa $it = 学习$it" }
        vm.openWriting()
        assertEquals(AppScreen.Writing, vm.currentScreen.value)
        assertEquals(20, vm.practicePairs.value.size)
        assertEquals((1..20).map { "nghĩa $it" }.toSet(), vm.practicePairs.value.map { it.vi }.toSet())
        val order = vm.practicePairs.value.toList()
        vm.syncWritingPosition(order[16].vi, order[16].foreign)
        assertEquals(16, vm.currentIndex.value)
        assertEquals(order, vm.practicePairs.value)
    }

    @Test fun blurModeSupportsFourStatesAndPersistsOffState() {
        val vm = TranslationViewModel(ApplicationProvider.getApplicationContext<Application>())
        // Start at 1 (mờ chữ Hán)
        vm.writingBlurMode.value = 1
        vm.saveWritingPrefs()
        assertEquals(1, vm.writingBlurMode.value)
        assertTrue(vm.writingBlur.value)

        // 1st press -> 2 (mờ tiếng Việt / pinyin & nghĩa)
        var next = if (vm.writingBlurMode.value >= 3) 0 else vm.writingBlurMode.value + 1
        vm.writingBlurMode.value = next
        vm.saveWritingPrefs()
        assertEquals(2, vm.writingBlurMode.value)
        assertTrue(vm.writingBlur.value)

        // 2nd press -> 3 (mờ cả hai)
        next = if (vm.writingBlurMode.value >= 3) 0 else vm.writingBlurMode.value + 1
        vm.writingBlurMode.value = next
        vm.saveWritingPrefs()
        assertEquals(3, vm.writingBlurMode.value)
        assertTrue(vm.writingBlur.value)

        // 3rd press / 4th step in cycle -> 0 (tắt làm mờ, hiện cả hai không mờ)
        next = if (vm.writingBlurMode.value >= 3) 0 else vm.writingBlurMode.value + 1
        vm.writingBlurMode.value = next
        vm.saveWritingPrefs()
        assertEquals(0, vm.writingBlurMode.value)
        assertFalse(vm.writingBlur.value)

        // Next press -> 1 (mờ chữ Hán trở lại)
        next = if (vm.writingBlurMode.value >= 3) 0 else vm.writingBlurMode.value + 1
        vm.writingBlurMode.value = next
        vm.saveWritingPrefs()
        assertEquals(1, vm.writingBlurMode.value)
        assertTrue(vm.writingBlur.value)
    }
}
