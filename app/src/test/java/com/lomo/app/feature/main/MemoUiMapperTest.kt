package com.lomo.app.feature.main

import com.lomo.domain.model.Memo
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoUiMapperTest {
    private val mapper = MemoUiMapper()

    @Test
    fun `mapToUiModel removes known tags from rendered body and keeps raw content`() {
        val memo =
            memo(
                content = "Meeting with C# team #work and #todo today.",
                tags = listOf("work", "todo"),
            )

        val uiModel = mapper.mapToUiModel(memo, rootPath = null, imagePath = null, imageMap = emptyMap())
        val renderedText = collectTextLiterals(uiModel.markdownNode.node).joinToString("\n")

        assertTrue(uiModel.processedContent.contains("#work"))
        assertTrue(uiModel.processedContent.contains("#todo"))
        assertTrue(renderedText.contains("C#"))
        assertFalse(renderedText.contains("#work"))
        assertFalse(renderedText.contains("#todo"))
    }

    @Test
    fun `mapToUiModel skips code blocks and links when erasing tags`() {
        val memo =
            memo(
                content =
                    """
                    ```kotlin
                    val raw = "#todo"
                    ```
                    [jump](https://example.com/#todo)
                    normal #todo text
                    """.trimIndent(),
                tags = listOf("todo"),
            )

        val uiModel = mapper.mapToUiModel(memo, rootPath = null, imagePath = null, imageMap = emptyMap())
        val root = uiModel.markdownNode.node
        val renderedText = collectTextLiterals(root).joinToString("\n")
        val codeLiterals = collectNodes(root).filterIsInstance<FencedCodeBlock>().map { it.literal.orEmpty() }
        val linkDestinations = collectNodes(root).filterIsInstance<Link>().map { it.destination.orEmpty() }

        assertTrue(codeLiterals.any { it.contains("#todo") })
        assertTrue(linkDestinations.any { it.contains("#todo") })
        assertFalse(renderedText.contains("normal #todo text"))
    }

    @Test
    fun `mapToUiModel prunes tag-only paragraph after erasing tags`() {
        val memo =
            memo(
                content =
                    """
                    #todo #work

                    body line
                    """.trimIndent(),
                tags = listOf("todo", "work"),
            )

        val uiModel = mapper.mapToUiModel(memo, rootPath = null, imagePath = null, imageMap = emptyMap())
        val paragraphs = collectNodes(uiModel.markdownNode.node).filterIsInstance<Paragraph>()
        val renderedText = collectTextLiterals(uiModel.markdownNode.node).joinToString("\n")

        assertEquals(1, paragraphs.size)
        assertTrue(renderedText.contains("body line"))
        assertFalse(renderedText.contains("#todo"))
        assertFalse(renderedText.contains("#work"))
    }

    private fun memo(
        content: String,
        tags: List<String>,
    ): Memo =
        Memo(
            id = "memo-1",
            timestamp = 0L,
            content = content,
            rawContent = content,
            date = "2026_02_23",
            tags = tags,
        )

    private fun collectTextLiterals(root: Node): List<String> =
        collectNodes(root)
            .filterIsInstance<Text>()
            .mapNotNull { it.literal }

    private fun collectNodes(root: Node): List<Node> {
        val nodes = mutableListOf<Node>()

        fun traverse(node: Node) {
            nodes += node
            var child = node.firstChild
            while (child != null) {
                val next = child.next
                traverse(child)
                child = next
            }
        }

        traverse(root)
        return nodes
    }
}
