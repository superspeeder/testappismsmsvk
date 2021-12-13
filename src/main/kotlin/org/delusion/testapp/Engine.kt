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
import java.nio.LongBuffer
import java.util.*

fun ptrOut(function: (it: PointerBuffer) -> Unit): Long {
    val pb = BufferUtils.createPointerBuffer(1)
    function(pb)
    return pb.rewind().get()
}

fun longOut(function: (it: LongBuffer) -> Unit): Long {
    val lb = BufferUtils.createLongBuffer(1)
    function(lb)
    return lb.rewind().get()
}

fun intOut(function: (it: IntBuffer) -> Unit): Int {
    val ib = BufferUtils.createIntBuffer(1)
    function(ib)
    return ib.rewind().get()
}

fun readPtrArr(function: (count: IntBuffer, buf: PointerBuffer?) -> Unit): PointerBuffer {
    val cib = BufferUtils.createIntBuffer(1)
    function(cib, null)
    val pb = BufferUtils.createPointerBuffer(cib.rewind().get())
    cib.rewind()
    function(cib, pb)
    return pb.rewind()
}

fun <T> getOr(v: T?, other: T): T {
    return v ?: other
}

class Engine {

    companion object globalstate {
        var glfwInitialized: Boolean = false;
    }


    private val enabledFeatures: VkPhysicalDeviceFeatures? = null
    private val deviceExtensions: MutableSet<String> = mutableSetOf(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
    private var window: Long? = null
    private var instance: VkInstance? = null
    private var physicalDevice: VkPhysicalDevice? = null
    private var surface: Long? = null
    private var device: VkDevice? = null

    private var name: ZString = ZString("KatEngine")
    private var version: Version = Version(1, 0, 0)

    private var graphicsFamily: Int? = null
    private var presentFamily: Int? = null

    private lateinit var app: App

    var instanceExtensions: MutableSet<String> = mutableSetOf()
    var instanceLayers: MutableSet<String> = mutableSetOf(
//        "VK_LAYER_LUNARG_api_dump"
    )


    fun init(app: App) {
        this.app = app
        if (!glfwInitialized) {
            glfwInit();
            glfwInitialized = true;
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)

        window = glfwCreateWindow(800, 800, "Window", NULL, NULL)

        createInstance()
        createSurface()
        pickPhysicalDevice()
        createDevice()
    }

    private fun createSurface() {
        instance?: throw RuntimeException("Instance must be created before surface")
        window?: throw RuntimeException("Window must be created before surface")
        instance?.let { inst ->
            window?.let { win ->
                surface = longOut { GLFWVulkan.glfwCreateWindowSurface(inst, win, null, it) }
            }
        }
    }

    private fun createDevice() {
        physicalDevice?: throw RuntimeException("Physical Device not found")
        surface?: throw RuntimeException("Surface must be created before device")
        physicalDevice?.let { pd ->
            surface?.let { surf ->


                val ib = BufferUtils.createIntBuffer(1)
                vkGetPhysicalDeviceQueueFamilyProperties(pd, ib.rewind(), null)
                val qfps = VkQueueFamilyProperties.create(ib.rewind().get())
                vkGetPhysicalDeviceQueueFamilyProperties(pd, ib.rewind(), qfps.rewind())

                qfps.rewind().forEachIndexed { i, props ->
                    if ((props.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0) {
                        graphicsFamily = i
                    }

                    if (intOut { KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(pd, i, surf, it) } == VK_TRUE) {
                        presentFamily = i
                    }

                    if (graphicsFamily != null && presentFamily != null) return@forEachIndexed
                }

                if (graphicsFamily == null || presentFamily == null) throw RuntimeException("Failed to locate required queue families")

                val qcis = VkDeviceQueueCreateInfo.create(if (graphicsFamily == presentFamily) 1 else 2)

                val qfp = BufferUtils.createFloatBuffer(1).rewind().put(1.0f).rewind()

                if (graphicsFamily == presentFamily) {
                    qcis[0].set(
                        VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                        0L,
                        0,
                        getOr(graphicsFamily, -1),
                        qfp
                    )
                } else {
                    qcis[0].set(
                        VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                        0L,
                        0,
                        getOr(graphicsFamily, -1),
                        qfp
                    )
                    qcis[1].set(
                        VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                        0L,
                        0,
                        getOr(presentFamily, -1),
                        qfp
                    )
                }

                val deviceExtensionszs = ZStringList(deviceExtensions.toMutableList())
                val dci = VkDeviceCreateInfo.create().set(
                    VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
                    0L,
                    0,
                    qcis,
                    null,
                    deviceExtensionszs.toBuf(),
                    enabledFeatures
                )

                val devh: Long = ptrOut { vkCreateDevice(pd, dci, null, it) }

                device = VkDevice(devh, pd, dci)
                println("Created Device")
                println("Device Extensions: " + deviceExtensions.toTypedArray().contentToString())
            }
        }
    }

    private fun pickPhysicalDevice() {
        instance?.let {
            val physdevs: PointerBuffer = readPtrArr { count, buf ->
                    vkEnumeratePhysicalDevices(it, count, buf)
            }


            if (physdevs.capacity() == 0) throw RuntimeException("Failed to locate valid physical device")
            physicalDevice = VkPhysicalDevice(physdevs.rewind().get(), it)

            val pdprop = VkPhysicalDeviceProperties.create()
            physicalDevice?.let { vkGetPhysicalDeviceProperties(it, pdprop) }
            println("Located Physical Device ${pdprop.deviceNameString()}")
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

        val instanceLayerszs = ZStringList(instanceLayers.toMutableList())
        val instanceExtensionszs = ZStringList(instanceExtensions.toMutableList())

        reqInstExt?.let { instanceExtensionszs.appendToBuf(it) }

        val exts_strs = instanceExtensionszs.readStrings()
        println("Instance Extensions: " + exts_strs.toTypedArray().contentToString())
        println("Instance Layers: " + instanceLayers.toTypedArray().contentToString())


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

        instance?.let { inst ->
            device?.let { dev ->
                vkDestroyDevice(dev, null)
            }

            surface?.let { KHRSurface.vkDestroySurfaceKHR(inst, it, null) }
            vkDestroyInstance(inst, null)
        }

        if (glfwInitialized) {
            window?.let {
                glfwDestroyWindow(it)
            }

            glfwTerminate()
            glfwInitialized = false
        }
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
