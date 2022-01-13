package io.github.thewisenerd.experiments

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import org.netbeans.lib.profiler.heap.HeapFactory
import org.netbeans.lib.profiler.heap.Instance
import org.netbeans.lib.profiler.heap.ObjectArrayInstance
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance

inline fun <reified T> Instance.cast(): T {
    if (this is T) return this
    error("instance not of type ${T::class.java}")
}

object Constants {
    const val EMPTY_STRING = ""
}

fun Instance.getString(): String? {
    val clazz = this.javaClass
    if (clazz.name != "java.lang.String") return null

    val array = this.getValueOfField("value") as PrimitiveArrayInstance
    if (array.values.isEmpty()) return Constants.EMPTY_STRING

    // let me read package private classes ,_,

    val buffer = StringBuffer(array.length)
    array.values.filterNotNull().forEach { buffer.append(it as String) }

    return buffer.toString()
}

fun dump(array: ObjectArrayInstance, os: OutputStream) {
    val len0 = array.values.size
    array.values.asSequence().mapNotNull { it as? Instance }.forEachIndexed { idx0, instance ->
        println("processing object $idx0 / $len0")
        val clazz = instance.javaClass
        when (clazz.name) {
            "java.util.Collections\$SingletonList" -> {
                val element = instance.getValueOfField("element")?.let { it as Instance }
                element?.getString()?.let {
                    os.write("$it\n".toByteArray())
                }
            }
            "java.util.ArrayList" -> {
                val elementData = instance.getValueOfField("elementData") as ObjectArrayInstance
                val len1 = elementData.values.size
                elementData.values.asSequence().mapNotNull { it as? Instance }.forEachIndexed { idx1, element ->
                    println("processing object $idx0 / $len0; $idx1 / $len1")
                    element.getString()?.let {
                        os.write("$it\n".toByteArray())
                    }
                }
            }
        }
    }

}

fun main() {
    val heapFile = File("/Users/thewisenerd/data/gc220113_01.hprof")
    val heap = HeapFactory.createHeap(heapFile)

    val outputFile = File("/Users/thewisenerd/data/logs_220113_01.json").outputStream().buffered()
    outputFile.use { os ->
        dump(heap.getInstanceByID(0x6422ac568).cast(), os)
    }

    println("debug break")
}