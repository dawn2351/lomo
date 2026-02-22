package com.lomo.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTokenizerTest {
    @Test
    fun `tokenize ASCII text`() {
        val input = "Hello World"
        val expected = "Hello World" // Tokenizer space splits
        assertEquals(expected, SearchTokenizer.tokenize(input))
    }

    @Test
    fun `tokenize CJK text`() {
        val input = "你好世界"
        // 你, 你好, 好, 好世, 世, 世界, 界
        val expected = "你 你好 好 好世 世 世界 界"
        assertEquals(expected, SearchTokenizer.tokenize(input))
    }

    @Test
    fun `tokenize Mixed text`() {
        val input = "Hello你好"
        val expected = "Hello 你 你好 好"
        assertEquals(expected, SearchTokenizer.tokenize(input))
    }

    @Test
    fun `tokenize CJK with spaces`() {
        val input = "我 爱 编程"
        val expected = "我 爱 编 编程 程"
        assertEquals(expected, SearchTokenizer.tokenize(input))
    }
}
