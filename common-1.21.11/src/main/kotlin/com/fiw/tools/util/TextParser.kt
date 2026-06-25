package com.fiw.tools.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object TextParser {
    private val CODE_TO_FORMATTING: Map<Char, ChatFormatting> = mapOf(
        '0' to ChatFormatting.BLACK, '1' to ChatFormatting.DARK_BLUE, '2' to ChatFormatting.DARK_GREEN, '3' to ChatFormatting.DARK_AQUA,
        '4' to ChatFormatting.DARK_RED, '5' to ChatFormatting.DARK_PURPLE, '6' to ChatFormatting.GOLD, '7' to ChatFormatting.GRAY,
        '8' to ChatFormatting.DARK_GRAY, '9' to ChatFormatting.BLUE, 'a' to ChatFormatting.GREEN, 'b' to ChatFormatting.AQUA,
        'c' to ChatFormatting.RED, 'd' to ChatFormatting.LIGHT_PURPLE, 'e' to ChatFormatting.YELLOW, 'f' to ChatFormatting.WHITE,
        'k' to ChatFormatting.OBFUSCATED, 'l' to ChatFormatting.BOLD, 'm' to ChatFormatting.STRIKETHROUGH,
        'n' to ChatFormatting.UNDERLINE, 'o' to ChatFormatting.ITALIC, 'r' to ChatFormatting.RESET
    )

    fun parse(input: String?): MutableComponent {
        if (input.isNullOrEmpty()) return Component.empty()
        val out: MutableComponent = Component.empty()
        var style = Style.EMPTY.withItalic(false)
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                out.append(Component.literal(buf.toString()).setStyle(style))
                buf.setLength(0)
            }
        }

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&' && i + 1 < input.length) {
                val code = input[i + 1].lowercaseChar()
                val fmt = CODE_TO_FORMATTING[code]
                if (fmt != null) {
                    flush()
                    style = if (fmt == ChatFormatting.RESET) {
                        Style.EMPTY.withItalic(false)
                    } else if (fmt.isColor) {
                        Style.EMPTY.withItalic(false).withColor(fmt)
                    } else {
                        applyFormatting(style, fmt)
                    }
                    i += 2
                    continue
                }
            }
            buf.append(c)
            i++
        }
        flush()
        return out
    }

    private fun applyFormatting(style: Style, fmt: ChatFormatting): Style = when (fmt) {
        ChatFormatting.BOLD -> style.withBold(true)
        ChatFormatting.ITALIC -> style.withItalic(true)
        ChatFormatting.UNDERLINE -> style.withUnderlined(true)
        ChatFormatting.STRIKETHROUGH -> style.withStrikethrough(true)
        ChatFormatting.OBFUSCATED -> style.withObfuscated(true)
        else -> style
    }
}
