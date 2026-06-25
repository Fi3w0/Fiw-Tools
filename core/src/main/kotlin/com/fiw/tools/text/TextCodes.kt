package com.fiw.tools.text

object TextCodes {
    private val VALID_CODES = setOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
        'k', 'l', 'm', 'n', 'o', 'r'
    )

    data class Segment(val text: String, val codes: Set<Char>)

    fun segments(input: String?): List<Segment> {
        if (input.isNullOrEmpty()) return emptyList()

        val out = mutableListOf<Segment>()
        val active = linkedSetOf<Char>()
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                out.add(Segment(buf.toString(), active.toSet()))
                buf.setLength(0)
            }
        }

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&' && i + 1 < input.length) {
                val code = input[i + 1].lowercaseChar()
                if (code in VALID_CODES) {
                    flush()
                    if (code == 'r') {
                        active.clear()
                    } else if (code in '0'..'9' || code in 'a'..'f') {
                        active.clear()
                        active.add(code)
                    } else {
                        active.add(code)
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
}
