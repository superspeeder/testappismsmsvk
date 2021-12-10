package org.delusion.testapp

import org.lwjgl.vulkan.VK10

class Version(val major: Int, val minor: Int, val patch: Int) {

    fun makeVK(): Int { return VK10.VK_MAKE_VERSION(major, minor, patch); }

}
