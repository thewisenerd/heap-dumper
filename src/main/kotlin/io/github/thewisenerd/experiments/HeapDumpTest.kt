package io.github.thewisenerd.experiments

import org.netbeans.lib.profiler.heap.*
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

inline fun <reified T> Instance.cast(): T {
    if (this is T) return this
    error("instance not of type ${T::class.java}")
}

object Constants {
    const val EMPTY_STRING = ""
    const val LATIN1: Byte = 0
    const val UTF16: Byte = 1
    const val ONE_MB = 1024 * 1024
    const val STR_LIMIT = 4 * ONE_MB

    const val ID_SIZE = 8

    val fileOffsetCls = Class.forName("org.netbeans.lib.profiler.heap.HprofObject")
    val fileOffsetAccessor = fileOffsetCls.getDeclaredField("fileOffset")

    init {
        fileOffsetAccessor.isAccessible = true
    }
}


fun Instance.getString(raf: RandomAccessFile): String? {
    val clazz = this.javaClass
    if (clazz.name != "java.lang.String") return null

    val coder = this.getValueOfField("coder") as Byte

    val array = this.getValueOfField("value") as PrimitiveArrayInstance
    if (array.values.isEmpty()) return Constants.EMPTY_STRING

    val strLength = array.length
    val needsTriage = array.length > Constants.STR_LIMIT

    val buffer = ByteArray(strLength)
    val fileOffset = Constants.fileOffsetAccessor.get(array) as Long
    val arrayStartOffset = fileOffset + 1 + Constants.ID_SIZE + 4 + 4 + 1

    raf.seek(arrayStartOffset)
    raf.read(buffer, 0, strLength)

    val decoded = when (coder) {
        Constants.LATIN1 -> String(buffer, Charsets.UTF_8)
        Constants.UTF16 -> String(buffer, Charsets.UTF_16LE)
        else -> throw IllegalArgumentException("unknown coder value=${coder.toInt()}")
    }

    if (needsTriage) {
        println("line needs triage: ${decoded.length}")
    }

    return decoded
}

fun dump(array: ObjectArrayInstance, raf: RandomAccessFile, os: OutputStream) {
    val len0 = array.values.size
    array.values.asSequence().mapNotNull { it as? Instance }.forEachIndexed { idx0, instance ->
        println("processing object $idx0 / $len0")
        val clazz = instance.javaClass
        when (clazz.name) {
            "java.util.Collections\$SingletonList" -> {
                val element = instance.getValueOfField("element")?.let { it as Instance }
                element?.getString(raf)?.let {
                    os.write("$it\n".toByteArray())
                }
            }

            "java.util.ArrayList" -> {
                val elementData = instance.getValueOfField("elementData") as ObjectArrayInstance
                val len1 = elementData.values.size
                elementData.values.asSequence().mapNotNull { it as? Instance }.forEachIndexed { idx1, element ->
                    println("processing object $idx0 / $len0; $idx1 / $len1")
                    element.getString(raf)?.let {
                        os.write("$it\n".toByteArray())
                    }
                }
            }

            "java.lang.String" -> {
                instance.getString(raf)?.let {
                    it.chunked(64 * Constants.ONE_MB).forEach { chunk ->
                        os.write(chunk.toByteArray())
                    }
                    os.write("\n".toByteArray())
                }
            }
        }
    }

}

fun main() {
    val heapFilePath = "/Users/thewisenerd/works/data/2024-08-23/java_pid1.hprof"
    val heapFile = File(heapFilePath)
    val heap = HeapFactory.createHeap(heapFile)

    val raf = RandomAccessFile(heapFile, "r")

    val outputFile =
        File("/Users/thewisenerd/works/data/2024-08-23/logs_240823_01_${System.currentTimeMillis()}.json").outputStream()
            .buffered()
    outputFile.use { os ->
        dump(heap.getInstanceByID(0x64a2a54d0).cast(), raf, os)
    }

    println("debug break")
}