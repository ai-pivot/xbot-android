package dev.xbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight native Markdown renderer.
 *
 * Supports the constructs xbot replies actually use: headings, paragraphs,
 * bullet/numbered lists, quotes, fenced code, inline bold/italic/code, links,
 * and native tables (scroll horizontally when wider than the screen).
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inline(block.text),
                    fontSize = when (block.level) {
                        1 -> 21.sp
                        2 -> 19.sp
                        3 -> 17.sp
                        else -> 15.sp
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MdBlock.Quote -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(2.dp),
                            )
                            .padding(horizontal = 1.5.dp, vertical = 2.dp),
                    ) {}
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = inline(item),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${index + 1}.",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = inline(item),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Code -> Column {
                    if (block.language.isNotBlank()) {
                        Text(
                            block.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            block.code.trimEnd(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                is MdBlock.Table -> MarkdownTable(block)

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        .padding(top = 0.5.dp),
                ) {}
            }
        }
    }
}

@Composable
fun MarkdownInline(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
) {
    Text(
        text = inline(text),
        modifier = modifier,
        style = style,
        textAlign = textAlign,
    )
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val columns = maxOf(
        table.headers.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
        1,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minColumn = 88.dp
        val columnWidth = maxOf(minColumn, maxWidth / columns.toFloat())
        val tableWidth = columnWidth * columns.toFloat()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(tableWidth)) {
                TableRow(
                    cells = table.headers,
                    columns = columns,
                    columnWidth = columnWidth,
                    alignments = table.alignments,
                    header = true,
                )
                table.rows.forEach { row ->
                    TableRow(
                        cells = row,
                        columns = columns,
                        columnWidth = columnWidth,
                        alignments = table.alignments,
                        header = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    columns: Int,
    columnWidth: Dp,
    alignments: List<TableAlign>,
    header: Boolean,
) {
    Row(modifier = Modifier.width(columnWidth * columns.toFloat())) {
        repeat(columns) { index ->
            val value = cells.getOrNull(index).orEmpty()
            val alignment = alignments.getOrNull(index) ?: TableAlign.Start
            Box(
                modifier = Modifier
                    .width(columnWidth)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    .background(
                        if (header) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                MarkdownInline(
                    text = value,
                    style = if (header) {
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    textAlign = when (alignment) {
                        TableAlign.Start -> TextAlign.Start
                        TableAlign.Center -> TextAlign.Center
                        TableAlign.End -> TextAlign.End
                    },
                )
            }
        }
    }
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val separator = lines[index + 1].trim()
    if (!header.contains('|') || !separator.contains('|')) return false
    val cells = splitTableRow(separator)
    return cells.isNotEmpty() && cells.all { it.matches(Regex(""":?-{3,}:?""")) }
}

private fun splitTableRow(row: String): List<String> {
    var value = row.trim()
    if (value.startsWith("|")) value = value.substring(1)
    if (value.endsWith("|")) value = value.dropLast(1)
    if (value.isBlank()) return emptyList()
    return value.split(Regex("""(?<!\\)\|""")).map {
        it.trim().replace("\\|", "|")
    }
}

private fun parseTableAlignments(separator: String): List<TableAlign> =
    splitTableRow(separator).map { cell ->
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        when {
            left && right -> TableAlign.Center
            right -> TableAlign.End
            else -> TableAlign.Start
        }
    }

@Composable
private fun inline(raw: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        var cursor = 0
        INLINE_REGEX.findAll(raw).forEach { match ->
            if (match.range.first > cursor) {
                appendAutoLinks(raw.substring(cursor, match.range.first), linkColor)
            }
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendAutoLinks(token.substring(2, token.length - 2), linkColor)
                    }
                }

                token.startsWith("`") && token.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            fontSize = 13.sp,
                        ),
                    ) {
                        append(token.substring(1, token.length - 1))
                    }
                }

                token.startsWith("[") -> {
                    val close = token.indexOf("](")
                    val end = token.lastIndexOf(')')
                    if (close > 0 && end > close + 2) {
                        val label = token.substring(1, close)
                        val url = token.substring(close + 2, end)
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append(label)
                        }
                    } else {
                        append(token)
                    }
                }

                token.startsWith("*") && token.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendAutoLinks(token.substring(1, token.length - 1), linkColor)
                    }
                }

                else -> appendAutoLinks(token, linkColor)
            }
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) appendAutoLinks(raw.substring(cursor), linkColor)
    }
}

/** Plain-text chat bubbles: auto-detect http(s):// and www. links. */
@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color? = null,
) {
    val resolvedLinkColor = linkColor ?: MaterialTheme.colorScheme.primary
    val annotated = remember(text, resolvedLinkColor) {
        buildAnnotatedString { appendAutoLinks(text, resolvedLinkColor) }
    }
    Text(text = annotated, modifier = modifier, style = style)
}

private val URL_REGEX = Regex("""(?i)\b(https?://|www\.)[^\s<>"']+""")

private fun AnnotatedString.Builder.appendAutoLinks(text: String, linkColor: Color) {
    var cursor = 0
    URL_REGEX.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        var display = match.value
        var suffix = ""
        while (display.isNotEmpty() && display.last() in ".,;:!?，。；：！？、") {
            suffix = display.last() + suffix
            display = display.dropLast(1)
        }
        while (display.endsWith(")") &&
            display.count { it == ')' } > display.count { it == '(' }
        ) {
            suffix = ")" + suffix
            display = display.dropLast(1)
        }
        if (display.isNotEmpty()) {
            val url = if (display.startsWith("www.", ignoreCase = true)) {
                "https://$display"
            } else {
                display
            }
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(display)
            }
        }
        append(suffix)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Numbered(val items: List<String>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlign>,
    ) : MdBlock
    data object Rule : MdBlock
}

private enum class TableAlign { Start, Center, End }

private val INLINE_REGEX = Regex("""(\*\*[^*\n]+\*\*|\*[^*\n]+\*|`[^`\n]+`|\[[^\]\n]+\]\([^)\n]+\))""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*\d+\.\s+(.*)$""")

private fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var index = 0
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            if (index < lines.size) index++
            blocks += MdBlock.Code(language, code.toString())
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            index++
            continue
        }

        val heading = HEADING.matchEntire(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(
                level = heading.groupValues[1].length,
                text = heading.groupValues[2],
            )
            index++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks += MdBlock.Rule
            index++
            continue
        }

        if (trimmed.startsWith("> ")) {
            flushParagraph()
            val quote = StringBuilder()
            while (index < lines.size && lines[index].trim().startsWith("> ")) {
                if (quote.isNotEmpty()) quote.append('\n')
                quote.append(lines[index].trim().removePrefix("> "))
                index++
            }
            blocks += MdBlock.Quote(quote.toString())
            continue
        }

        if (BULLET.matches(line)) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (index < lines.size && BULLET.matches(lines[index])) {
                items += BULLET.matchEntire(lines[index])!!.groupValues[1]
                index++
            }
            blocks += MdBlock.Bullets(items)
            continue
        }

        if (NUMBERED.matches(line)) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (index < lines.size && NUMBERED.matches(lines[index])) {
                items += NUMBERED.matchEntire(lines[index])!!.groupValues[1]
                index++
            }
            blocks += MdBlock.Numbered(items)
            continue
        }

        if (isTableStart(lines, index)) {
            flushParagraph()
            val headers = splitTableRow(lines[index])
            val alignments = parseTableAlignments(lines[index + 1])
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size) {
                val row = lines[index].trim()
                if (row.isEmpty() || !row.contains('|')) break
                rows += splitTableRow(row)
                index++
            }
            blocks += MdBlock.Table(headers, rows, alignments)
            continue
        }

        paragraph += line
        index++
    }
    flushParagraph()
    return blocks
}
