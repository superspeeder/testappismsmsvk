package org.delusion.testapp

import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK.*
import org.lwjgl.vulkan.VK12.*
import java.lang.RuntimeException
import java.nio.IntBuffer
import java.util.*

fun ptrOut(function: (it: PointerBuffer) -> Unit): Long {
    val pb = BufferUtils.createPointerBuffer(1)
    function(pb)
    return pb.rewind().get()
}

fun readPtrArr(function: (count: IntBuffer, buf: PointerBuffer?) -> Unit): PointerBuffer {
    val cib = BufferUtils.createIntBuffer(1)
    function(cib, null)
    val pb = BufferUtils.createPointerBuffer(cib.rewind().get())
    cib.rewind()
    function(cib, pb)
    return pb.rewind()
}

class Engine {

    companion object globalstate {
        var glfwInitialized: Boolean = false;
    }


    private var window: Long? = null
    private var instance: VkInstance? = null
    private var physicalDevice: VkPhysicalDevice? = null

    private var name: ZString = ZString("KatEngine")
    private var version: Version = Version(1, 0, 0)

    private lateinit var app: App

    var instanceExtensions: MutableList<String> = mutableListOf()
    var instanceLayers: MutableList<String> = mutableListOf()


    fun init(app: App) {
        this.app = app
        if (!glfwInitialized) {
            glfwInit();
            glfwInitialized = true;
        }


        window = glfwCreateWindow(800, 800, "Window", NULL, NULL)

        createInstance()
        pickPhysicalDevice()

        val pdprop = VkPhysicalDeviceProperties.malloc()
        physicalDevice?.let { vkGetPhysicalDeviceProperties(it, pdprop) }
        println("Located Physical Device ${pdprop.deviceNameString()}")


    }

    private fun pickPhysicalDevice() {
        instance?.let {
            val physdevs: PointerBuffer = readPtrArr { count, buf ->
                    vkEnumeratePhysicalDevices(it, count, buf)
            }


            if (physdevs.capacity() == 0) throw RuntimeException("Failed to locate valid physical device")
            physicalDevice = VkPhysicalDevice(physdevs.rewind().get(), it)
        }
        instance?: throw RuntimeException("Instance not initialized")
    }

    private fun createInstance() {
        val reqInstExt: PointerBuffer? = GLFWVulkan.glfwGetRequiredInstanceExtensions()

        val appInfo: VkApplicationInfo = VkApplicationInfo.create().set(
            VK_STRUCTURE_TYPE_APPLICATION_INFO,
            NULL,
            app.name.toBuf(),
            app.version.makeVK(),
            name.toBuf(),
            version.makeVK(),
            VK_API_VERSION_1_2
        )

        val instanceLayerszs = ZStringList(instanceLayers)
        val instanceExtensionszs = ZStringList(instanceExtensions)

        reqInstExt?.let { instanceExtensionszs.appendToBuf(it) }

        val exts_strs = instanceExtensionszs.readStrings()
        println(Arrays.toString(exts_strs.toTypedArray()))


        val ici: VkInstanceCreateInfo = VkInstanceCreateInfo.create().set(
            VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
            NULL,
            0,
            appInfo,
            instanceLayerszs.toBuf(),
            instanceExtensionszs.toBuf()
        )

        val instH: Long = ptrOut { vkCreateInstance(ici, null, it) }
        instance = VkInstance(instH, ici)
    }

    fun isOpen(): Boolean {
        return window?.let { return !glfwWindowShouldClose(it); } ?: false
    }

    fun cleanup() {
        if (glfwInitialized) {
            window?.let {
                glfwDestroyWindow(it)
            }

            glfwTerminate()
            glfwInitialized = false
        }

        instance?.let { vkDestroyInstance(it, null) }
    }

    fun preRender() {
        glfwPollEvents()
    }

    fun postRender() {

    }

    fun whileOpen(act: () -> Unit) {
        while (isOpen()) {
            preRender()
            act()
            postRender()
        }
    }


}
