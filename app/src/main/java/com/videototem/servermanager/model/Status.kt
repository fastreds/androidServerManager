package com.videototem.servermanager.model

enum class State { OK, WARN, FAIL, UNKNOWN }

data class Status(
    val state: State,
    val detail: String,
    val ts: Long = System.currentTimeMillis()
)

data class Pm2Proc(
    val name: String,
    val status: String,
    val restarts: Int,
    val cpu: Double,
    val memMb: Long
)

fun pm2State(status: String): State = when (status) {
    "online" -> State.OK
    "launching" -> State.WARN
    else -> State.FAIL
}

data class SshAccess(
    val host: String,
    val port: String,
    val user: String,
    val password: String
) {
    val command: String get() = "ssh $user@$host -p $port"
}

data class UiState(
    val root: Status = Status(State.UNKNOWN, "sin comprobar"),
    val termux: Status = Status(State.UNKNOWN, "sin comprobar"),
    val ubuntu: Status = Status(State.UNKNOWN, "sin comprobar"),
    val pm2: Status = Status(State.UNKNOWN, "sin comprobar"),
    val pm2Procs: List<Pm2Proc> = emptyList(),
    val tailscale: Status = Status(State.UNKNOWN, "sin comprobar"),
    val ssh: Status = Status(State.UNKNOWN, "sin comprobar"),
    val sshAccess: SshAccess? = null,
    val health: Status = Status(State.UNKNOWN, "sin comprobar"),
    val busy: Boolean = false,
    val lastAction: String = ""
)
