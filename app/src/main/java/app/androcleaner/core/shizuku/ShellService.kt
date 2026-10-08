package app.androcleaner.core.shizuku

import androidx.annotation.Keep
import app.androcleaner.IShellService
import kotlin.system.exitProcess

/** Shizuku user service: executes shell commands with the shell (adb) identity. */
@Keep
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
