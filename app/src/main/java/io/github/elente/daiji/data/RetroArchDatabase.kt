package io.github.elente.daiji.data

import java.io.DataInputStream
import java.io.File
import java.io.IOException

/**
 * Reads libretro `.rdb` files: a 16-byte header, then one msgpack map per game,
 * closed by a nil. See RetroArch's `libretro-db/libretrodb.c` and `rmsgpack.c`.
 */
object RetroArchDatabase {
    private const val MAGIC = "RARCHDB"
    private const val MAGIC_FIELD_SIZE = 8
    private const val BUFFER_SIZE = 256 * 1024

    /** Info keyed by ROM name without extension, lowercase. Names the database lacks are absent. */
    fun lookup(file: File, romNames: Set<String>): Map<String, GameInfo> {
        if (!file.isFile || romNames.isEmpty()) return emptyMap()
        val found = HashMap<String, GameInfo>()
        runCatching {
            DataInputStream(file.inputStream().buffered(BUFFER_SIZE)).use { input ->
                readHeader(input)
                val reader = MsgPackReader(input)
                while (found.size < romNames.size) {
                    val entry = reader.read() as? Map<*, *> ?: break
                    val name = romName(entry) ?: continue
                    if (name in romNames) found.putIfAbsent(name, info(entry))
                }
            }
        }
        return found
    }

    private fun readHeader(input: DataInputStream) {
        val magic = ByteArray(MAGIC_FIELD_SIZE).also(input::readFully)
        if (String(magic, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) throw IOException("Not a libretro database")
        input.readLong()
    }

    private fun romName(entry: Map<*, *>): String? =
        ((entry["rom_name"] as? String)?.substringBeforeLast('.') ?: entry["name"] as? String)?.lowercase()

    private fun info(entry: Map<*, *>) = GameInfo(
        year = intField(entry, "releaseyear"),
        players = intField(entry, "users"),
        genre = entry["genre"] as? String,
        developer = entry["developer"] as? String,
        publisher = entry["publisher"] as? String,
    )

    private fun intField(entry: Map<*, *>, key: String): Int? = when (val value = entry[key]) {
        is Long -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private class MsgPackReader(private val input: DataInputStream) {
        fun read(): Any? {
            val tag = input.readUnsignedByte()
            return when {
                tag < 0x80 -> tag.toLong()
                tag >= 0xe0 -> (tag - 0x100).toLong()
                tag < 0x90 -> readMap(tag and 0x0f)
                tag < 0xa0 -> readArray(tag and 0x0f)
                tag < 0xc0 -> readString(tag and 0x1f)
                else -> readTagged(tag)
            }
        }

        private fun readTagged(tag: Int): Any? = when (tag) {
            0xc0 -> null
            0xc2 -> false
            0xc3 -> true
            0xc4 -> readBytes(input.readUnsignedByte())
            0xc5 -> readBytes(input.readUnsignedShort())
            0xc6 -> readBytes(input.readInt())
            0xca -> input.readFloat().toDouble()
            0xcb -> input.readDouble()
            0xcc -> input.readUnsignedByte().toLong()
            0xcd -> input.readUnsignedShort().toLong()
            0xce -> input.readInt().toLong() and 0xffffffffL
            0xcf, 0xd3 -> input.readLong()
            0xd0 -> input.readByte().toLong()
            0xd1 -> input.readShort().toLong()
            0xd2 -> input.readInt().toLong()
            0xd9 -> readString(input.readUnsignedByte())
            0xda -> readString(input.readUnsignedShort())
            0xdb -> readString(input.readInt())
            0xdc -> readArray(input.readUnsignedShort())
            0xdd -> readArray(input.readInt())
            0xde -> readMap(input.readUnsignedShort())
            0xdf -> readMap(input.readInt())
            else -> throw IOException("Unsupported msgpack tag $tag")
        }

        private fun readMap(size: Int): Map<Any?, Any?> =
            HashMap<Any?, Any?>(size * 2).also { map -> repeat(size) { map[read()] = read() } }

        private fun readArray(size: Int): List<Any?> = List(size) { read() }

        private fun readString(size: Int): String = String(readBytes(size), Charsets.UTF_8)

        private fun readBytes(size: Int): ByteArray = ByteArray(size).also(input::readFully)
    }
}
