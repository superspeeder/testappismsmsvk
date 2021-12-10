package org.delusion.testapp

abstract class App {
    private val engine: Engine = Engine()
    var name: ZString = ZString("App")
    var version: Version = Version(1,0,0);

    fun run() {
        engine.init(this)
        create()

        engine.whileOpen {
            render()
        }
        destroy()

        engine.cleanup()
    }

    abstract fun create()
    abstract fun render()
    abstract fun destroy()
}