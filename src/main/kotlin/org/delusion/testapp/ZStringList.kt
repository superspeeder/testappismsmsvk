package org.delusion.testapp

import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil

class ZStringList(var strs: MutableList<String>) {
    private var zstrs: MutableList<ZString> = strs.map { ZString(it) }.toMutableList()
    private var pbuf: PointerBuffer = BufferUtils.createPointerBuffer(zstrs.size)

    init {
        zstrs.forEach {
            pbuf.put(it.toBuf())
        }
        pbuf.rewind()
    }

    fun toBuf(): PointerBuffer = pbuf

    // Unsafe appending, doesn't preserve integrity of strs or zstrs, but they aren't used outside of init
    fun appendToBufUnsafe(ptrs: PointerBuffer) {
        val npbuf: PointerBuffer = BufferUtils.createPointerBuffer(pbuf.capacity() + ptrs.capacity())
        npbuf.put(pbuf).put(ptrs);
        pbuf = npbuf
    }

    fun appendToBuf(ptrs: PointerBuffer) {
        for (i in 0 until ptrs.capacity()) {
            val ptr: Long = ptrs.get(i)
            strs.add(MemoryUtil.memASCII(ptr))
            zstrs.add(ZString(strs[strs.size - 1]))
        }

        reconstruct()
    }

    private fun reconstruct() {
        pbuf = BufferUtils.createPointerBuffer(zstrs.size)
        zstrs.forEach {
            pbuf.put(it.toBuf())
        }
        pbuf.rewind()
    }

    fun readStrings(): List<String> {
        val str_ls = mutableListOf<String>()
        for (i in 0 until pbuf.capacity()) {
            val ptr: Long = pbuf.get(i)
            str_ls.add(MemoryUtil.memASCII(ptr))
        }
        return str_ls
    }

}
