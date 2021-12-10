package org.delusion.testapp

import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.nio.CharBuffer

class ZString(text: String) {
    private var buf: ByteBuffer = BufferUtils.createByteBuffer(text.encodeToByteArray().size + 1)

    init {
        text.encodeToByteArray().forEach {
            buf.put(it)
        }
        buf.put(0).rewind()
    }

    fun toBuf(): ByteBuffer = buf

}
