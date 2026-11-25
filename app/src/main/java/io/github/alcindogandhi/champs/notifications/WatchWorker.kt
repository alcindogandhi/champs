package io.github.alcindogandhi.champs.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.alcindogandhi.champs.ChampsApp
import java.time.Duration

/**
 * Consulta de segurança: periódica e em laço quando o serviço em primeiro plano não pôde iniciar. Também
 * mantém os jogos do time do coração na lista de acompanhados.
 */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val watcher = (applicationContext as ChampsApp).container.watcher
        watcher.syncFavorite()
        val result = if (LiveMatchService.running) null else watcher.poll()
        watcher.reschedule()
        // Ainda há jogo na janela e o serviço não está de pé: consulta de novo (no mínimo em 1 min).
        if (result != null && result.active.isNotEmpty() && !LiveMatchService.running) {
            watcher.scheduleFallbackPoll(maxOf(result.nextDelay, Duration.ofMinutes(1)))
        }
        return Result.success()
    }
}
