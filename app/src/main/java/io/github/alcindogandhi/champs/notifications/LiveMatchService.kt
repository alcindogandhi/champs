package io.github.alcindogandhi.champs.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.alcindogandhi.champs.ChampsApp
import io.github.alcindogandhi.champs.data.db.FollowedMatchEntity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Serviço em primeiro plano ativo só durante os jogos acompanhados: consulta a cada 30 s e mostra uma
 * notificação discreta com os placares. Encerra sozinho quando nenhum jogo está mais na janela.
 */
class LiveMatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private val container get() = (application as ChampsApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground precisa vir logo: o sistema encerra o app se demorar.
        ServiceCompat.startForeground(
            this,
            MatchNotifier.WATCHING_ID,
            container.notifier.watching(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        running = true
        if (loop?.isActive != true) loop = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        val watcher = container.watcher
        while (scope.isActive) {
            val result = try {
                watcher.poll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LiveMatchService", "Falha ao consultar os jogos", e)
                null
            }
            if (result == null || result.active.isEmpty()) break
            updateNotification(result.active)
            delay(result.nextDelay.toMillis().coerceAtLeast(MIN_DELAY.toMillis()))
        }
        finish()
    }

    @Suppress("MissingPermission") // a notificação do serviço é sempre permitida enquanto ele roda
    private fun updateNotification(active: List<FollowedMatchEntity>) {
        if (container.notifier.canNotify()) {
            NotificationManagerCompat.from(this).notify(MatchNotifier.WATCHING_ID, container.notifier.watching(active))
        }
    }

    private suspend fun finish() {
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        container.watcher.reschedule()
    }

    /** Android 15+: serviços do tipo dataSync têm limite de 6 h por dia; ao esgotar, segue pelo WorkManager. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        running = false
        container.watcher.scheduleFallbackPoll(Duration.ZERO)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val MIN_DELAY: Duration = Duration.ofSeconds(30)

        @Volatile
        var running = false
            private set
    }
}
