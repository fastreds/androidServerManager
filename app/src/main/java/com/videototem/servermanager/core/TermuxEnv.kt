package com.videototem.servermanager.core

import android.content.Context

class TermuxEnv(val cfg: Config) {
    val prefix: String = cfg.termuxPrefix
    val home: String = "/data/data/com.termux/files/home"
    val logDir: String = "$home/server-manager"
    val bash: String = "$prefix/bin/bash"
    val prootDistro: String get() = "$prefix/bin/proot-distro"
    val sshd: String get() = "$prefix/bin/sshd"

    fun prootLogin(inner: String): String =
        "$prootDistro login ${cfg.distro} -- bash -lc ${Cmd.sh(inner)}"

    fun envPrelude(): String =
        "export PATH=$prefix/bin:\$PATH HOME=$home LD_LIBRARY_PATH=$prefix/lib TMPDIR=$prefix/tmp;"

    /** Ejecuta un comando dentro de Ubuntu (proot) en background, con log */
    fun ubuntuBackground(inner: String): String =
        "mkdir -p $logDir; nohup $prootDistro login ${cfg.distro} -- bash -lc ${Cmd.sh(inner)} " +
            ">> $logDir/server.log 2>&1 &"

    /** Consulta: ejecuta dentro de Ubuntu y devuelve stdout al canal */
    fun ubuntuQuery(inner: String): String = prootLogin(inner)
}
