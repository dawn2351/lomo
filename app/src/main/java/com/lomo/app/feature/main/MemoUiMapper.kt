package com.lomo.app.feature.main

import android.net.Uri
import com.lomo.domain.model.Memo
import com.lomo.ui.component.markdown.ImmutableNode
import com.lomo.ui.component.markdown.MarkdownParser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import java.io.File
import javax.inject.Inject

class MemoUiMapper
    @Inject
    constructor() {
        suspend fun mapToUiModels(
            memos: List<Memo>,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
            deletingIds: Set<String> = emptySet(),
        ): List<MemoUiModel> =
            withContext(Dispatchers.Default) {
                memos.map { memo ->
                    mapToUiModel(
                        memo = memo,
                        rootPath = rootPath,
                        imagePath = imagePath,
                        imageMap = imageMap,
                        isDeleting = memo.id in deletingIds,
                    )
                }
            }

        fun mapToUiModel(
            memo: Memo,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
            isDeleting: Boolean = false,
        ): MemoUiModel {
            val processedContent = buildProcessedContent(memo.content, rootPath, imagePath, imageMap)
            val parsedNode = applyTagEraser(MarkdownParser.parse(processedContent), memo.tags)
            val imageUrls = extractImageUrls(processedContent)

            return MemoUiModel(
                memo = memo,
                processedContent = processedContent,
                markdownNode = parsedNode,
                tags = memo.tags.toImmutableList(),
                imageUrls = imageUrls,
                isDeleting = isDeleting,
            )
        }

        private fun buildProcessedContent(
            content: String,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
        ): String {
            var resolvedContent = content

            resolvedContent =
                WIKI_IMAGE_REGEX.replace(resolvedContent) { match ->
                    val path = match.groupValues[1]
                    val resolved =
                        resolveImageModel(path, isWikiStyle = true, rootPath = rootPath, imagePath = imagePath, imageMap = imageMap)
                    val finalUrl = (resolved as? File)?.absolutePath ?: resolved.toString()
                    "![]($finalUrl)"
                }

            resolvedContent =
                MARKDOWN_IMAGE_REGEX.replace(resolvedContent) { match ->
                    val alt = match.groupValues[1]
                    val path = match.groupValues[2]

                    if (AUDIO_EXTENSIONS.any { path.lowercase().endsWith(it) }) {
                        match.value
                    } else {
                        val resolved =
                            resolveImageModel(
                                imageUrl = path,
                                isWikiStyle = false,
                                rootPath = rootPath,
                                imagePath = imagePath,
                                imageMap = imageMap,
                            )
                        val finalUrl = (resolved as? File)?.absolutePath ?: resolved.toString()
                        "![$alt]($finalUrl)"
                    }
                }

            return resolvedContent
        }

        private fun resolveImageModel(
            imageUrl: String,
            isWikiStyle: Boolean,
            rootPath: String?,
            imagePath: String?,
            imageMap: Map<String, Uri>,
        ): Any {
            val cacheKey = if (imageUrl.startsWith("../")) imageUrl.substringAfterLast("/") else imageUrl
            imageMap[cacheKey]?.let { return it }

            if (imageUrl.startsWith("/") || imageUrl.startsWith("content://") || imageUrl.startsWith("http")) {
                return imageUrl
            }

            val basePath = if (isWikiStyle) (imagePath ?: rootPath) else rootPath
            if (basePath != null) {
                if (basePath.startsWith("content://") || basePath.startsWith("file://")) {
                    if (imageUrl.startsWith("../")) {
                        return imageUrl
                    }
                    return Uri
                        .parse(basePath)
                        .buildUpon()
                        .appendPath(imageUrl)
                        .build()
                        .toString()
                }

                return if (imageUrl.startsWith("../")) {
                    val parentDir = File(basePath).parent ?: basePath
                    File(parentDir, imageUrl.removePrefix("../"))
                } else {
                    File(basePath, imageUrl)
                }
            }
            return imageUrl
        }

        private fun extractImageUrls(content: String): ImmutableList<String> {
            val imageUrls = mutableListOf<String>()
            EXTRACT_IMAGE_URL_REGEX.findAll(content).forEach { match ->
                val url = match.groupValues[1]
                if (url.isNotBlank()) {
                    imageUrls.add(url)
                }
            }
            return imageUrls.toImmutableList()
        }

        private fun applyTagEraser(
            root: ImmutableNode,
            tags: List<String>,
        ): ImmutableNode {
            val normalizedTags =
                tags
                    .asSequence()
                    .map { it.trim().trimStart('#').trimEnd('/') }
                    .filter { it.isNotBlank() }
                    .toSet()
            if (normalizedTags.isEmpty()) return root

            val tagPatterns = normalizedTags.map(::createTagPattern)
            stripKnownTags(root.node, tagPatterns)
            trimParagraphEdges(root.node)
            pruneEmptyParagraphs(root.node)
            return root
        }

        private fun stripKnownTags(
            node: Node,
            tagPatterns: List<Regex>,
        ) {
            if (shouldSkipNode(node)) return

            if (node is Text) {
                node.literal = eraseTagsInText(node.literal.orEmpty(), tagPatterns)
                return
            }

            var child = node.firstChild
            while (child != null) {
                val next = child.next
                stripKnownTags(child, tagPatterns)
                child = next
            }
        }

        private fun shouldSkipNode(node: Node): Boolean =
            node is Heading ||
                node is Link ||
                node is FencedCodeBlock ||
                node is IndentedCodeBlock ||
                node is Code

        private fun eraseTagsInText(
            input: String,
            tagPatterns: List<Regex>,
        ): String {
            if ('#' !in input) return input

            var stripped = input
            var changed = false

            tagPatterns.forEach { pattern ->
                val updated =
                    stripped.replace(pattern) { match ->
                        changed = true
                        match.groupValues[1]
                    }
                stripped = updated
            }

            if (!changed) return input

            return stripped
                .replace(HORIZONTAL_WHITESPACE_REGEX, " ")
                .replace(SPACE_BEFORE_PUNCTUATION_REGEX, "")
                .replace(SPACE_BEFORE_NEWLINE_REGEX, "\n")
                .replace(LEADING_EMPTY_LINES_REGEX, "")
        }

        private fun createTagPattern(tag: String): Regex = Regex("(^|\\s)#${Regex.escape(tag)}(?:/)?(?=\\s|$|[.,!?;:，。！？；：、)\\]}】）])")

        private fun trimParagraphEdges(root: Node) {
            traverse(root) { node ->
                if (node is Paragraph) {
                    trimParagraphEdge(node, fromStart = true)
                    trimParagraphEdge(node, fromStart = false)
                }
            }
        }

        private fun trimParagraphEdge(
            paragraph: Paragraph,
            fromStart: Boolean,
        ) {
            var cursor: Node? = if (fromStart) paragraph.firstChild else paragraph.lastChild
            while (cursor != null && isParagraphEdgeJunk(cursor)) {
                val next = if (fromStart) cursor.next else cursor.previous
                cursor.unlink()
                cursor = next
            }
        }

        private fun isParagraphEdgeJunk(node: Node): Boolean =
            node is SoftLineBreak ||
                node is HardLineBreak ||
                (node is Text && node.literal.orEmpty().isBlank())

        private fun pruneEmptyParagraphs(root: Node) {
            val emptyParagraphs = mutableListOf<Paragraph>()
            traverse(root) { node ->
                if (node is Paragraph && !hasRenderableContent(node)) {
                    emptyParagraphs += node
                }
            }
            emptyParagraphs.forEach { it.unlink() }
        }

        private fun hasRenderableContent(node: Node): Boolean =
            when (node) {
                is Text -> {
                    node.literal?.any { !it.isWhitespace() } == true
                }

                is SoftLineBreak, is HardLineBreak -> {
                    false
                }

                is Code -> {
                    !node.literal.isNullOrBlank()
                }

                is FencedCodeBlock -> {
                    !node.literal.isNullOrBlank()
                }

                is IndentedCodeBlock -> {
                    !node.literal.isNullOrBlank()
                }

                is Image -> {
                    true
                }

                else -> {
                    var child = node.firstChild
                    while (child != null) {
                        if (hasRenderableContent(child)) {
                            return true
                        }
                        child = child.next
                    }
                    false
                }
            }

        private fun traverse(
            root: Node,
            visit: (Node) -> Unit,
        ) {
            visit(root)
            var child = root.firstChild
            while (child != null) {
                val next = child.next
                traverse(child, visit)
                child = next
            }
        }

        private companion object {
            private val WIKI_IMAGE_REGEX = Regex("!\\[\\[(.*?)\\]\\]")
            private val MARKDOWN_IMAGE_REGEX = Regex("!\\[(.*?)\\]\\((.*?)\\)")
            private val EXTRACT_IMAGE_URL_REGEX = Regex("!\\[.*?\\]\\((.*?)\\)")
            private val AUDIO_EXTENSIONS = setOf(".m4a", ".mp3", ".aac", ".wav")
            private val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t]{2,}")
            private val SPACE_BEFORE_PUNCTUATION_REGEX = Regex("[ \\t]+(?=[.,!?;:，。！？；：、)\\]}】）])")
            private val SPACE_BEFORE_NEWLINE_REGEX = Regex("[ \\t]+(?=\\n)")
            private val LEADING_EMPTY_LINES_REGEX = Regex("^(?:[ \\t]*\\n)+")
        }
    }
