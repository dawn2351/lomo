package com.lomo.app.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lomo.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Utility object for sharing and copying memo content.
 */
object ShareUtils {
    private const val MAX_SHARE_CONTENT_CHARS = 4000
    private val markdownTextProcessor =
        com.lomo.data.util
            .MemoTextProcessor()

    private data class ShareCardConfig(
        val style: String,
        val showTime: Boolean,
        val timestampMillis: Long?,
        val tags: List<String>,
        val activeDayCount: Int?,
    )

    private data class ShareCardPalette(
        val bgStart: Int,
        val bgEnd: Int,
        val card: Int,
        val cardBorder: Int,
        val bodyText: Int,
        val secondaryText: Int,
        val tagBg: Int,
        val tagText: Int,
        val divider: Int,
        val shadow: Int,
        val surfaceHighlightStart: Int,
        val surfaceHighlightEnd: Int,
    )

    /**
     * Share memo content as an image card via Android share sheet.
     */
    fun shareMemoAsImage(
        context: Context,
        content: String,
        title: String? = null,
        style: String = "warm",
        showTime: Boolean = true,
        timestamp: Long? = null,
        tags: List<String> = emptyList(),
        activeDayCount: Int? = null,
    ) {
        runCatching {
            val imageUri =
                createShareImageUri(
                    context = context,
                    content = content,
                    title = title,
                    config =
                        ShareCardConfig(
                            style = style,
                            showTime = showTime,
                            timestampMillis = timestamp,
                            tags = tags,
                            activeDayCount = activeDayCount,
                        ),
                )
            val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(context.contentResolver, "memo_image", imageUri)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }.onFailure {
            // Fallback to text share so user still can complete the action.
            shareMemoText(context, content, title)
        }
    }

    /**
     * Share memo content via Android share sheet as plain text.
     */
    fun shareMemoText(
        context: Context,
        content: String,
        title: String? = null,
    ) {
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, content)
                type = "text/plain"
                title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    /**
     * Copy content to clipboard and show a toast confirmation.
     */
    fun copyToClipboard(
        context: Context,
        content: String,
        showToast: Boolean = true,
    ) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lomo Memo", content)
        clipboardManager.setPrimaryClip(clip)

        if (showToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    /**
     * Share memo as markdown file (for future enhancement).
     */
    fun shareMemoAsMarkdown(
        context: Context,
        content: String,
        fileName: String,
    ) {
        // TODO: Create temp file and share via FileProvider
        // For now, just share as text
        shareMemoText(context, content, fileName)
    }

    private fun createShareImageUri(
        context: Context,
        content: String,
        title: String?,
        config: ShareCardConfig,
    ): android.net.Uri {
        val bitmap = createMemoCardBitmap(context, content, title, config)
        val dir = File(context.cacheDir, "shared_memos").apply { mkdirs() }
        val file = File(dir, "memo_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun createMemoCardBitmap(
        context: Context,
        content: String,
        title: String?,
        config: ShareCardConfig,
    ): Bitmap {
        val resources = context.resources
        val density = resources.displayMetrics.density

        fun sp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                resources.displayMetrics,
            )

        val tags = buildShareTags(config.tags, content)
        val bodyTextWithoutTags = removeInlineTags(content, tags)
        val renderedMarkdownText = renderMarkdownForShare(context, bodyTextWithoutTags)
        val safeText =
            renderedMarkdownText
                .trim()
                .ifEmpty { context.getString(R.string.app_name) }
                .let {
                    if (it.length <= MAX_SHARE_CONTENT_CHARS) {
                        it
                    } else {
                        it.take(MAX_SHARE_CONTENT_CHARS) + "\n…"
                    }
                }

        val canvasWidth = (resources.displayMetrics.widthPixels.coerceAtLeast(720) * 0.9f).toInt()
        val outerPadding = 24f * density
        val cardPadding = 26f * density
        val cardRadius = 28f * density
        val minCardHeight = 330f * density
        val cardWidth = canvasWidth - outerPadding * 2f
        val textMaxWidth = cardWidth - cardPadding * 2f
        val chipHorizontalPadding = 12f * density
        val chipVerticalPadding = 6.5f * density
        val chipSpacing = 8f * density
        val chipRowSpacing = 8f * density
        val sectionGap = 20f * density
        val footerTopGap = 20f * density
        val palette = resolvePalette(config.style)
        val createdAtMillis = config.timestampMillis ?: System.currentTimeMillis()
        val createdAtText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAtMillis))
        val activeDayCountText =
            config.activeDayCount
                ?.takeIf { it > 0 }
                ?.let { dayCount ->
                    context.resources.getQuantityString(R.plurals.share_card_recorded_days, dayCount, dayCount)
                }.orEmpty()
        val showFooter = config.showTime || activeDayCountText.isNotBlank()
        val preferCenteredBody = safeText.length <= 28 && safeText.lines().size <= 2
        val displayTitle = title?.takeIf { it.isNotBlank() }

        fun weightedTypeface(weight: Int): Typeface {
            val baseTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(baseTypeface, weight.coerceIn(100, 900), false)
            } else {
                Typeface.create(baseTypeface, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        val contentWeight = safeText.length + safeText.lines().size * 14
        val bodyTextSize =
            when {
                contentWeight <= 24 -> sp(28f)
                contentWeight <= 64 -> sp(24f)
                contentWeight <= 130 -> sp(20f)
                contentWeight <= 220 -> sp(18f)
                contentWeight <= 360 -> sp(16.5f)
                else -> sp(15.5f)
            }

        val bodyPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.bodyText
                textSize = bodyTextSize
                letterSpacing = 0.008f
                typeface = weightedTypeface(480)
            }

        val tagPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.tagText
                textSize = sp(12f)
                letterSpacing = 0.018f
                typeface = weightedTypeface(620)
            }

        val footerPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = sp(13f)
                letterSpacing = 0.01f
                typeface = weightedTypeface(560)
            }

        val bodyLayout =
            StaticLayout.Builder
                .obtain(safeText, 0, safeText.length, bodyPaint, textMaxWidth.toInt())
                .setAlignment(if (preferCenteredBody) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()

        val titleLayout =
            if (displayTitle.isNullOrBlank()) {
                null
            } else {
                val titlePaint =
                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.secondaryText
                        textSize = sp(14.5f)
                        letterSpacing = 0.028f
                        typeface = weightedTypeface(580)
                    }
                StaticLayout.Builder
                    .obtain(displayTitle, 0, displayTitle.length, titlePaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setMaxLines(1)
                    .build()
            }

        val tagRows =
            buildTagRows(
                tags = tags,
                paint = tagPaint,
                maxWidth = textMaxWidth,
                chipHorizontalPadding = chipHorizontalPadding,
                chipSpacing = chipSpacing,
            )
        val tagTextHeight = tagPaint.fontMetrics.run { descent - ascent }
        val tagChipHeight = tagTextHeight + chipVerticalPadding * 2f
        val tagsBlockHeight =
            if (tagRows.isEmpty()) {
                0f
            } else {
                tagRows.size * tagChipHeight + (tagRows.size - 1) * chipRowSpacing
            }
        val titleBlockHeight = titleLayout?.height?.toFloat() ?: 0f
        val topBlockHeight = tagsBlockHeight + titleBlockHeight
        val topGap = if (topBlockHeight > 0f) sectionGap else 0f
        val footerTextHeight = footerPaint.fontMetrics.run { descent - ascent }
        val footerBlockHeight = if (showFooter) footerTopGap + footerTextHeight else 0f
        val requiredHeight =
            cardPadding +
                topBlockHeight +
                topGap +
                bodyLayout.height +
                footerBlockHeight +
                cardPadding
        val cardHeight = requiredHeight.coerceAtLeast(minCardHeight)
        val canvasHeight = ceil(cardHeight + outerPadding * 2f).toInt()

        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        canvasWidth.toFloat(),
                        canvasHeight.toFloat(),
                        palette.bgStart,
                        palette.bgEnd,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), bgPaint)

        val cardRect =
            RectF(
                outerPadding,
                outerPadding,
                outerPadding + cardWidth,
                outerPadding + cardHeight,
            )

        val shadowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.shadow
                setShadowLayer(16f * density, 0f, 9f * density, palette.shadow)
            }
        canvas.drawRoundRect(
            RectF(cardRect.left, cardRect.top + 4f * density, cardRect.right, cardRect.bottom + 4f * density),
            cardRadius + 1f * density,
            cardRadius + 1f * density,
            shadowPaint,
        )

        val cardPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.card
            }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)

        val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.cardBorder
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
            }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

        val highlightPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        cardRect.left,
                        cardRect.top,
                        cardRect.left,
                        cardRect.top + cardRect.height() * 0.58f,
                        palette.surfaceHighlightStart,
                        palette.surfaceHighlightEnd,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, highlightPaint)

        var topCursor = cardRect.top + cardPadding
        val tagBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.tagBg
            }
        if (tagRows.isNotEmpty()) {
            tagRows.forEach { row ->
                var x = cardRect.left + cardPadding
                row.forEach { tag ->
                    val chipText = "#$tag"
                    val chipWidth = tagPaint.measureText(chipText) + chipHorizontalPadding * 2f
                    val chipRect = RectF(x, topCursor, x + chipWidth, topCursor + tagChipHeight)
                    canvas.drawRoundRect(chipRect, tagChipHeight / 2f, tagChipHeight / 2f, tagBgPaint)
                    val baseline = topCursor + chipVerticalPadding - tagPaint.fontMetrics.top
                    canvas.drawText(chipText, x + chipHorizontalPadding, baseline, tagPaint)
                    x += chipWidth + chipSpacing
                }
                topCursor += tagChipHeight + chipRowSpacing
            }
            topCursor -= chipRowSpacing
        }
        if (titleLayout != null) {
            if (topCursor > cardRect.top + cardPadding) {
                topCursor += 8f * density
            }
            canvas.save()
            canvas.translate(cardRect.left + cardPadding, topCursor)
            titleLayout.draw(canvas)
            canvas.restore()
            topCursor += titleLayout.height
        }

        val bodyTop = (topCursor + if (topBlockHeight > 0f) sectionGap else 0f)
        val bodyBottom = cardRect.bottom - cardPadding - footerBlockHeight
        val bodyAvailable = (bodyBottom - bodyTop).coerceAtLeast(bodyLayout.height.toFloat())
        val bodyDrawTop = bodyTop + (bodyAvailable - bodyLayout.height) / 2f

        canvas.save()
        canvas.translate(cardRect.left + cardPadding, bodyDrawTop)
        bodyLayout.draw(canvas)
        canvas.restore()

        if (showFooter) {
            val footerTop = cardRect.bottom - cardPadding - footerTextHeight
            val footerBaseline = footerTop - footerPaint.fontMetrics.top
            val dividerY = footerTop - footerTopGap * 0.55f
            val dividerPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.divider
                    style = Paint.Style.STROKE
                    strokeWidth = 0.9f * density
                }
            canvas.drawLine(
                cardRect.left + cardPadding,
                dividerY,
                cardRect.right - cardPadding,
                dividerY,
                dividerPaint,
            )

            val leftText = if (config.showTime) createdAtText else ""
            val footerGap = 10f * density
            val footerAvailableWidth = textMaxWidth
            val hasLeft = leftText.isNotBlank()
            val hasRight = activeDayCountText.isNotBlank()

            val (displayLeft, displayRight) =
                when {
                    hasLeft && hasRight -> {
                        // Prioritize right-side "recorded days" visibility for longer English strings.
                        val minLeftWidth = footerPaint.measureText("00-00 00:00")
                        val rightMaxWidth =
                            (footerAvailableWidth - footerGap - minLeftWidth)
                                .coerceAtLeast(footerAvailableWidth * 0.45f)
                                .coerceAtMost(footerAvailableWidth - footerGap)
                        val resolvedRight = ellipsizeToWidth(activeDayCountText, rightMaxWidth, footerPaint)
                        val leftMaxWidth =
                            (footerAvailableWidth - footerGap - footerPaint.measureText(resolvedRight))
                                .coerceAtLeast(0f)
                        val resolvedLeft = ellipsizeToWidth(leftText, leftMaxWidth, footerPaint)
                        resolvedLeft to resolvedRight
                    }

                    hasLeft -> {
                        ellipsizeToWidth(leftText, footerAvailableWidth, footerPaint) to ""
                    }

                    hasRight -> {
                        "" to ellipsizeToWidth(activeDayCountText, footerAvailableWidth, footerPaint)
                    }

                    else -> {
                        "" to ""
                    }
                }

            if (displayLeft.isNotBlank()) {
                canvas.drawText(displayLeft, cardRect.left + cardPadding, footerBaseline, footerPaint)
            }
            if (displayRight.isNotBlank()) {
                val rightX = cardRect.right - cardPadding - footerPaint.measureText(displayRight)
                canvas.drawText(displayRight, rightX, footerBaseline, footerPaint)
            }
        }

        return bitmap
    }

    private fun buildShareTags(
        sourceTags: List<String>,
        content: String,
    ): List<String> {
        val normalized =
            sourceTags
                .asSequence()
                .map { it.trim().trimStart('#') }
                .filter { it.isNotBlank() }
                .map { it.take(18) }
                .distinct()
                .take(6)
                .toList()
        if (normalized.isNotEmpty()) return normalized

        val regex = Regex("(?:^|\\s)#([\\p{L}\\p{N}_][\\p{L}\\p{N}_/]*)")
        return regex
            .findAll(content)
            .map { it.groupValues[1] }
            .map { it.take(18) }
            .distinct()
            .take(6)
            .toList()
    }

    private fun removeInlineTags(
        content: String,
        tags: List<String>,
    ): String {
        val explicitTags =
            tags
                .asSequence()
                .map { it.trim().trimStart('#') }
                .filter { it.isNotBlank() }
                .toList()

        var stripped = content
        explicitTags.forEach { tag ->
            val escaped = Regex.escape(tag)
            stripped =
                stripped.replace(Regex("(^|\\s)#$escaped(?=\\s|$)")) { match ->
                    if (match.value.startsWith(" ") || match.value.startsWith("\t")) " " else ""
                }
        }

        // Fallback generic cleanup in case tags were parsed from content rather than sourceTags.
        stripped =
            stripped.replace(Regex("(^|\\s)#[\\p{L}\\p{N}_][\\p{L}\\p{N}_/]*")) { match ->
                if (match.value.startsWith(" ") || match.value.startsWith("\t")) " " else ""
            }
        stripped = stripped.replace(Regex(" {2,}"), " ")
        stripped = stripped.replace(Regex("\\n{3,}"), "\n\n")
        return stripped.trim()
    }

    private fun renderMarkdownForShare(
        context: Context,
        content: String,
    ): String {
        var str = content.replace("\r\n", "\n")

        // Convert fenced code blocks to indented plain text blocks.
        str =
            str.replace(Regex("```[\\w-]*\\n([\\s\\S]*?)```")) { match ->
                val code = match.groupValues[1].trim('\n')
                if (code.isBlank()) {
                    ""
                } else {
                    code
                        .lineSequence()
                        .joinToString("\n") { "    $it" }
                }
            }

        // Convert audio markdown attachments before generic markdown stripping.
        str =
            str.replace(
                Regex("!\\[[^\\]]*\\]\\(([^)]+\\.(?:m4a|mp3|aac|wav))\\)", RegexOption.IGNORE_CASE),
                context.getString(R.string.share_card_placeholder_audio),
            )

        str = markdownTextProcessor.stripMarkdown(str)
        str = str.replace("[Image]", context.getString(R.string.share_card_placeholder_image))
        str =
            str.replace(Regex("\\[Image:\\s*(.*?)]")) { match ->
                context.getString(R.string.share_card_placeholder_image_named, match.groupValues[1])
            }
        str = str.replace(Regex("`([^`]+)`"), "「$1」")
        str = str.replace(Regex("~~(.*?)~~"), "$1")
        str = str.replace(Regex("(?m)^>\\s?"), "│ ")
        str = str.replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), "")
        str = str.replace(Regex("\\n{3,}"), "\n\n")
        return str.trim()
    }

    private fun buildTagRows(
        tags: List<String>,
        paint: TextPaint,
        maxWidth: Float,
        chipHorizontalPadding: Float,
        chipSpacing: Float,
        maxRows: Int = 2,
    ): List<List<String>> {
        if (tags.isEmpty() || maxRows <= 0) return emptyList()

        val rows = mutableListOf<MutableList<String>>()
        var currentRow = mutableListOf<String>()
        var currentWidth = 0f

        tags.forEach { tag ->
            val chipText = "#$tag"
            val chipWidth = paint.measureText(chipText) + chipHorizontalPadding * 2f
            if (currentRow.isEmpty()) {
                currentRow.add(tag)
                currentWidth = chipWidth
                return@forEach
            }

            if (currentWidth + chipSpacing + chipWidth <= maxWidth) {
                currentRow.add(tag)
                currentWidth += chipSpacing + chipWidth
            } else {
                rows.add(currentRow)
                if (rows.size >= maxRows) return@forEach
                currentRow = mutableListOf(tag)
                currentWidth = chipWidth
            }
        }

        if (rows.size < maxRows && currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        return rows
    }

    private fun ellipsizeToWidth(
        text: String,
        maxWidth: Float,
        paint: TextPaint,
    ): String {
        if (text.isBlank() || maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun resolvePalette(style: String): ShareCardPalette =
        when (style) {
            "clean" -> {
                ShareCardPalette(
                    bgStart = 0xFFF6F8FF.toInt(),
                    bgEnd = 0xFFE7EEFF.toInt(),
                    card = 0xFFFFFFFF.toInt(),
                    cardBorder = 0x2E5F86CB,
                    bodyText = 0xFF1D2A43.toInt(),
                    secondaryText = 0xFF687897.toInt(),
                    tagBg = 0x1F4E7ECB,
                    tagText = 0xFF385EAA.toInt(),
                    divider = 0x334F79C4,
                    shadow = 0x17000000,
                    surfaceHighlightStart = 0x26FFFFFF,
                    surfaceHighlightEnd = 0x00FFFFFF,
                )
            }

            "dark" -> {
                ShareCardPalette(
                    bgStart = 0xFF111722.toInt(),
                    bgEnd = 0xFF0B1118.toInt(),
                    card = 0xFF1C2530.toInt(),
                    cardBorder = 0x3F86A2C8,
                    bodyText = 0xFFEAF2FB.toInt(),
                    secondaryText = 0xFF93A7BF.toInt(),
                    tagBg = 0x326786B0,
                    tagText = 0xFFD7E7FF.toInt(),
                    divider = 0x336E8EB6,
                    shadow = 0x31000000,
                    surfaceHighlightStart = 0x16CFE1FF,
                    surfaceHighlightEnd = 0x00CFE1FF,
                )
            }

            else -> {
                ShareCardPalette(
                    bgStart = 0xFFFFF6E5.toInt(),
                    bgEnd = 0xFFF9E5C1.toInt(),
                    card = 0xFFFFFEFA.toInt(),
                    cardBorder = 0x33BE9350,
                    bodyText = 0xFF2F2414.toInt(),
                    secondaryText = 0xFF8A7962.toInt(),
                    tagBg = 0x24C89A4B,
                    tagText = 0xFF6A4E1E.toInt(),
                    divider = 0x33B18847,
                    shadow = 0x18000000,
                    surfaceHighlightStart = 0x1AFFFFFF,
                    surfaceHighlightEnd = 0x00FFFFFF,
                )
            }
        }
}
