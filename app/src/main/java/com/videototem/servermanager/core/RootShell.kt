package com.videototem.servermanager.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.TimeUnit

data class ShellResult(
    val code: Int,
    val out: List<String>,
    val err: List<String>
) {
    val isSuccess: Boolean get() = code == 0
}

object Cmd {
    fun sh(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}

object RootShell {

    private val suCandidates = listOf("su", "/system/xbin/su", "/system/bin/su", "/su/bin/su", "/magisk/.core/bin/su")

    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val outBuf = Collections.synchronizedList(ArrayList<String>())
        val errBuf = Collections.synchronizedList(ArrayList<String>())
        try {
            var last: Exception? = null
            var p: Process? = null
            for (su in suCandidates) {
                try {
                    p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(false).start()
                    break
                } catch (e: Exception) { last = e }
            }
            if (p == null) throw last ?: Exception("su no encontrado")
            val outReader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { outBuf.add(it) } }
            }
            val errReader = Thread {
                runCatching { p.errorStream.bufferedReader().forEachLine { errBuf.add(it) } }
            }
            outReader.start()
            errReader.start()
            val finished = p.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                ShellResult(-1, emptyList(), listOf("timeout tras 30s"))
            } else {
                outReader.join(3000)
                errReader.join(3000)
                ShellResult(p.exitValue(), ArrayList(outBuf), ArrayList(errBuf))
            }
        } catch (e: Exception) {
            ShellResult(-1, ArrayList(outBuf), ArrayList(errBuf) + (e.message ?: e.javaClass.simpleName))
        }
    }

    suspend fun rootAvailable(): Boolean {
        val r = exec("id")
        return r.isSuccess && r.out.any { it.contains("uid=0") }
    }

    fun resultText(r: ShellResult): String {
        val out = r.out.joinToString("\n").trim()
        val err = r.err.joinToString("\n").trim()
        return listOf(out, err).filter { it.isNotBlank() }.joinToString(" | ").ifBlank { "rc=${r.code}" }
    }
}
