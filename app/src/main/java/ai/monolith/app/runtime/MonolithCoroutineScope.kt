package ai.monolith.app.runtime

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-owned background execution for Monolith feature modules.
 *
 * The owner must call [close] from Activity.onDestroy(). File/network/model work belongs on IO;
 * CPU-heavy deterministic work belongs on Default. Failures are routed to the supplied callback
 * instead of escaping a coroutine and terminating the process.
 */
class MonolithCoroutineScope(
    private val onFailure: (String, Throwable) -> Unit
) {
    private val closed = AtomicBoolean(false)
    private val supervisor = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        val name = context[CoroutineName]?.name ?: "MonolithCoroutine"
        onFailure(name, throwable)
    }
    private val scope = CoroutineScope(supervisor + Dispatchers.Main.immediate + exceptionHandler)

    @JvmOverloads
    fun launchIo(name: String = "MonolithIO", task: Runnable) {
        if (closed.get()) return
        scope.launch(Dispatchers.IO + CoroutineName(name)) {
            task.run()
        }
    }

    @JvmOverloads
    fun launchDefault(name: String = "MonolithDefault", task: Runnable) {
        if (closed.get()) return
        scope.launch(Dispatchers.Default + CoroutineName(name)) {
            task.run()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel("Monolith owner destroyed")
    }
}
