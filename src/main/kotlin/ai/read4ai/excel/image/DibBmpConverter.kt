package ai.read4ai.excel.image

import java.io.ByteArrayOutputStream

internal object DibBmpConverter {

    fun convertDibToBmp(dib: ByteArray): ByteArray? {
        if (dib.size < 40) return null
        val biSize = readIntLE(dib, 0)
        if (biSize < 40 || biSize > dib.size) return null
        val bitCount = readUShortLE(dib, 14)
        val clrUsed = readIntLE(dib, 32)

        val paletteEntries = if (bitCount <= 8) {
            if (clrUsed != 0) clrUsed else 1 shl bitCount
        } else 0
        val paletteBytes = paletteEntries * 4
        val pixelOffset = 14 + biSize + paletteBytes
        val fileSize = 14 + dib.size

        val header = ByteArray(14)
        header[0] = 'B'.code.toByte()
        header[1] = 'M'.code.toByte()
        writeIntLE(header, 2, fileSize)
        writeShortLE(header, 6, 0)
        writeShortLE(header, 8, 0)
        writeIntLE(header, 10, pixelOffset)

        val baos = ByteArrayOutputStream(fileSize)
        baos.write(header)
        baos.write(dib)
        return baos.toByteArray()
    }

    private fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun readUShortLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun writeIntLE(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xFF).toByte()
        buf[off + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xFF).toByte()
        buf[off + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
