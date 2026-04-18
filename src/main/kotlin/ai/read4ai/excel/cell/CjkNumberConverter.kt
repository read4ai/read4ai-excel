package ai.read4ai.excel.cell

internal object CjkNumberConverter {

    fun digitToCJK(ch: Char): Char {
        return when (ch) {
            '0' -> '\u96F6'
            '1' -> '\u4E00'
            '2' -> '\u4E8C'
            '3' -> '\u4E09'
            '4' -> '\u56DB'
            '5' -> '\u4E94'
            '6' -> '\u516D'
            '7' -> '\u4E03'
            '8' -> '\u516B'
            '9' -> '\u4E5D'
            else -> ch
        }
    }

    fun toCJKNumber(n: Long): String {
        if (n == 0L) return "\u96F6"
        if (n < 0) return "\u8CA0" + toCJKNumber(-n)

        val digits = arrayOf("\u96F6", "\u4E00", "\u4E8C", "\u4E09", "\u56DB", "\u4E94", "\u516D", "\u4E03", "\u516B", "\u4E5D")
        val units = arrayOf("", "\u5341", "\u767E", "\u5343")

        fun fourDigit(num: Long): String {
            var numVar = num
            val parts = ArrayList<String>(4)
            var zeroPending = false
            var unitPos = 0
            while (numVar > 0 && unitPos < 4) {
                val d = (numVar % 10).toInt()
                if (d == 0) {
                    zeroPending = parts.isNotEmpty()
                } else {
                    val seg = buildString {
                        if (zeroPending) append("\u96F6")
                        append(digits[d])
                        append(units[unitPos])
                    }
                    parts.add(seg)
                    zeroPending = false
                }
                numVar /= 10
                unitPos++
            }
            if (parts.isEmpty()) return "\u96F6"
            val res = parts.reversed().joinToString("")
            return if (num in 10..19) res.removePrefix("\u4E00") else res
        }

        val bigUnits = arrayOf("", "\u4E07", "\u5104")
        var remain = n
        var idx = 0
        val chunks = ArrayList<String>()
        while (remain > 0 && idx < bigUnits.size) {
            val part = (remain % 10000)
            if (part != 0L) {
                val section = fourDigit(part)
                chunks.add(section + bigUnits[idx])
            }
            remain /= 10000
            idx++
        }
        return chunks.reversed().joinToString("")
    }
}
