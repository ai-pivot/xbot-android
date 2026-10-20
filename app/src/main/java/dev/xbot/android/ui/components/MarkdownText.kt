package dev.xbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight native Markdown renderer.
 *
 * Supports the constructs xbot replies actually use: headings, paragraphs,
 * bullet/numbered lists, quotes, fenced code, inline bold/italic/code and links.
 * Tables are rendered as monospace blocks (still readable, no layout surprises).
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
private fun inline(raw: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        var cursor = 0
        INLINE_REGEX.findAll(raw).forEach { match ->
            if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.substring(2, token.length - 2))
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
                    if (close > 0) {
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            ),
                        ) {
                            append(token.substring(1, close))
                        }
                    } else {
                        append(token)
                    }
                }

                token.startsWith("*") && token.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.substring(1, token.length - 1))
                    }
                }

                else -> append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) append(raw.substring(cursor))
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Numbered(val items: List<String>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
}

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

        // Table rows: keep the alignment readable without a layout engine.
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            flushParagraph()
            val table = mutableListOf<String>()
            while (index < lines.size) {
                val row = lines[index].trim()
                if (!row.startsWith("|") || !row.endsWith("|")) break
                table += row
                index++
            }
            blocks += MdBlock.Code("table", table.joinToString("\n"))
            continue
        }

        paragraph += line
        index++
    }
    flushParagraph()
    return blocks
}
