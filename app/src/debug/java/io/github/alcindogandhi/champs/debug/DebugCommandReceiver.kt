package io.github.alcindogandhi.champs.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.alcindogandhi.champs.ChampsApp
import io.github.alcindogandhi.champs.data.db.FollowedMatchEntity
import io.github.alcindogandhi.champs.domain.MatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Comandos de teste das notificações, só no build de debug:
 *
 * adb shell am broadcast -a io.github.alcindogandhi.champs.debug.REWIND -p io.github.alcindogandhi.champs \
 *   --es slug bra.1 --es event 401841317 --es date 2026-11-24 --ei home 1 --ei away 0
 *
 * Acompanha o jogo como se estivesse ao vivo com o placar informado e põe o relógio do vigia 30 min depois do
 * início real. Na próxima consulta, o app compara com o placar real e avisa os gols que faltam. O relógio
 * volta ao normal quando o processo reinicia.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REWIND) return
        val slug = intent.getStringExtra("slug") ?: return
        val event = intent.getStringExtra("event") ?: return
        val date = intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now()
        val home = intent.getIntExtra("home", 0)
        val away = intent.getIntExtra("away", 0)
        val container = (context.applicationContext as ChampsApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val match = container.football.matchesOn(slug, date).last().data.first { it.id == event }
                // O vigia passa a ver o relógio 30 min depois do início real do jogo.
                val kickoff = requireNotNull(match.date)
                container.watcherClockOffset = Duration.between(Instant.now(), kickoff.plusSeconds(30 * 60))
                container.database.followedMatches().upsert(
                    FollowedMatchEntity(
                        eventId = match.id,
                        competitionSlug = slug,
                        homeId = match.home.team.id,
                        homeName = match.home.team.shortName,
                        awayId = match.away.team.id,
                        awayName = match.away.team.shortName,
                        kickoff = kickoff.toEpochMilli(),
                        lastState = MatchState.LIVE.name,
                        lastHome = home,
                        lastAway = away,
                        auto = false,
                        followedAt = System.currentTimeMillis(),
                    ),
                )
                container.watcher.reschedule()
                Log.i(TAG, "Rebobinado: ${match.home.team.shortName} $home x $away ${match.away.team.shortName}")
            } catch (e: Exception) {
                Log.e(TAG, "Falha no REWIND", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ChampsDebug"
        const val ACTION_REWIND = "io.github.alcindogandhi.champs.debug.REWIND"
    }
}
