package io.github.alcindogandhi.champs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.alcindogandhi.champs.ChampsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Executa [block] fora da thread principal mantendo o receptor vivo até terminar. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (MatchWatcher) -> Unit) {
    val pending = goAsync()
    val watcher = (context.applicationContext as ChampsApp).container.watcher
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block(watcher)
        } finally {
            pending.finish()
        }
    }
}

/** Alarme do início de um jogo acompanhado: liga o serviço (alarmes exatos permitem isso em segundo plano). */
class WatchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) { it.reschedule() }
}

/** Alarmes não sobrevivem ao desligar o aparelho nem à mudança de permissão: reagenda. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Receptor exportado: só aceita as ações do sistema declaradas no manifesto.
        if (intent.action !in ACTIONS) return
        runAsync(context) { it.reschedule() }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
