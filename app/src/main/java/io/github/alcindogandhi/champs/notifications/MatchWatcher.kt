package io.github.alcindogandhi.champs.notifications

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.alcindogandhi.champs.data.FootballDataSource
import io.github.alcindogandhi.champs.data.db.FollowedMatchDao
import io.github.alcindogandhi.champs.data.db.FollowedMatchEntity
import io.github.alcindogandhi.champs.data.settings.SettingsRepository
import io.github.alcindogandhi.champs.domain.LastSeen
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchEvents
import io.github.alcindogandhi.champs.domain.MatchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Acompanha jogos para avisar início e gols, sem servidor: o próprio aparelho consulta a ESPN.
 *
 * - Na hora de um jogo acompanhado, o [LiveMatchService] (serviço em primeiro plano) consulta a cada 30 s com a
 *   bola rolando e a cada 1 min antes do início e no intervalo (2 e 3 min com a economia de bateria).
 * - Para acordar no início, um alarme exato dispara 2 min antes. Alarmes exatos também permitem iniciar o
 *   serviço com o app em segundo plano.
 * - Sem essa permissão, ou se o sistema não deixar iniciar o serviço, o [WatchWorker] consulta em laço
 *   (que o Android pode espaçar com o aparelho em repouso).
 * - Um trabalho periódico serve de rede de segurança (15 min, com jogo acompanhado) e inclui os jogos do time
 *   do coração (6 h). Sem nada a acompanhar, não há trabalho em segundo plano.
 */
class MatchWatcher(
    private val context: Context,
    private val dao: FollowedMatchDao,
    private val football: FootballDataSource,
    private val settings: SettingsRepository,
    private val notifier: MatchNotifier,
    private val now: () -> Instant = Instant::now,
) {
    private val pollLock = Mutex()

    fun observeFollowedIds(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    suspend fun follow(match: Match, auto: Boolean = false) {
        // Guarda o estado atual: só o que acontecer daqui em diante vira aviso.
        dao.upsert(match.toEntity(auto, existing = dao.get(match.id)))
        reschedule()
    }

    suspend fun unfollow(eventId: String) {
        dao.delete(eventId)
        reschedule()
    }

    /** Inclui os próximos jogos do time do coração (ou tira, se o aviso foi desligado). */
    suspend fun syncFavorite() {
        val current = settings.settings.first()
        dao.deleteUpcomingAuto()
        val favorite = current.favorite
        if (favorite != null && current.notifyFavorite) {
            val fixtures = runCatching { football.teamFixtures(favorite.competitionSlug, favorite.team.id).last().data }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList())
            val horizon = now().plus(Duration.ofDays(14))
            fixtures.filter { it.status.state == MatchState.SCHEDULED && (it.date ?: horizon) < horizon }
                .forEach { match ->
                    // Jogo que o usuário já segue manualmente continua manual.
                    val existing = dao.get(match.id)
                    if (existing == null || existing.auto) dao.upsert(match.toEntity(auto = true, existing = existing))
                }
        }
        dao.deleteOlderThan(now().minus(Duration.ofDays(2)).toEpochMilli())
    }

    /** Resultado de uma consulta: jogos que seguem na janela e a espera até a próxima consulta. */
    data class PollResult(val active: List<FollowedMatchEntity>, val nextDelay: Duration)

    /**
     * Consulta os jogos acompanhados que estão na janela de jogo, manda os avisos e guarda o novo estado.
     *
     * Um pedido por campeonato e dia da ESPN (o placar do dia, que já traz os gols). Jogo que não estiver
     * nesse dia é procurado nos dias vizinhos, só se preciso.
     */
    suspend fun poll(): PollResult = pollLock.withLock {
        val due = dao.pending().filter { it.isDue() }
        if (due.isEmpty()) return PollResult(emptyList(), Duration.ZERO)
        val seen = mutableListOf<Match>()
        due.groupBy { it.competitionSlug to it.kickoffOrNow() }.forEach { (key, list) ->
            val (slug, kickoff) = key
            val found = fetchDay(slug, kickoff, 0).orEmpty().associateBy { it.id }.toMutableMap()
            if (list.any { it.eventId !in found }) {
                for (offset in listOf(-1L, 1L)) fetchDay(slug, kickoff, offset)?.forEach { found.putIfAbsent(it.id, it) }
            }
            for (followed in list) {
                val match = found[followed.eventId] ?: continue
                seen += match
                MatchEvents.detect(followed.lastSeen(), match).forEach { notifier.show(followed, it) }
                dao.upsert(match.toEntity(followed.auto, existing = followed))
            }
        }
        val batterySaver = settings.settings.first().batterySaver
        PollResult(dao.pending().filter { it.isDue() }, MatchEvents.pollInterval(seen, batterySaver))
    }

    private suspend fun fetchDay(slug: String, kickoff: Instant, offset: Long): List<Match>? =
        runCatching { football.matchesOfEspnDay(slug, kickoff, offset, force = true).last().data }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Falha ao consultar $slug ($kickoff, $offset)", it)
            }
            .getOrNull()

    /** Liga o serviço se há jogo na janela, agenda o alarme do próximo início e ajusta o trabalho periódico. */
    suspend fun reschedule() {
        val pending = dao.pending()
        val current = settings.settings.first()
        val watchingFavorite = current.favorite != null && current.notifyFavorite
        if (!notifier.canNotify()) {
            cancelAlarm()
            schedulePeriodic(null)
            return
        }
        // Jogo acompanhado começando nas próximas 6 h: rede de segurança a cada 15 min. Jogos mais distantes ou
        // só o time do coração: a cada 6 h basta (o alarme do início chama reschedule e encurta o intervalo).
        // Nada a acompanhar: nenhum trabalho em segundo plano.
        val soon = now().plus(SOON)
        val startingSoon = pending.any { it.isDue() || it.kickoff?.let(Instant::ofEpochMilli)?.isBefore(soon) == true }
        schedulePeriodic(
            when {
                startingSoon -> Duration.ofMinutes(15)
                pending.isNotEmpty() || watchingFavorite -> SOON
                else -> null
            },
        )
        if (pending.isEmpty()) {
            cancelAlarm()
            return
        }
        if (pending.any { it.isDue() }) startWatching()
        val next = pending.mapNotNull { it.kickoff?.let(Instant::ofEpochMilli) }
            .map(MatchEvents::watchStart)
            .filter { it > now() }
            .minOrNull()
        if (next != null) setAlarm(next) else cancelAlarm()
    }

    @Volatile
    private var periodicInterval: Duration? = Duration.ZERO // desconhecido até o primeiro reschedule

    /** Trabalho periódico de segurança e sincronização do time do coração; null cancela. */
    private fun schedulePeriodic(interval: Duration?) {
        if (interval == periodicInterval) return
        periodicInterval = interval
        val work = WorkManager.getInstance(context)
        if (interval == null) {
            work.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<WatchWorker>(interval.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Laço sem serviço em primeiro plano: uma consulta por vez enquanto houver jogo na janela. */
    fun scheduleFallbackPoll(delay: Duration = Duration.ofMinutes(1)) {
        val request = OneTimeWorkRequestBuilder<WatchWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LOOP_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager().canScheduleExactAlarms()

    private fun startWatching() {
        if (LiveMatchService.running) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, LiveMatchService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+: sem alarme exato ou app em primeiro plano, o sistema não deixa iniciar o serviço.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e !is ForegroundServiceStartNotAllowedException) throw e
            Log.i(TAG, "Serviço não pôde iniciar em segundo plano; consultando pelo WorkManager")
            scheduleFallbackPoll(Duration.ZERO)
        }
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WatchAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(at: Instant) {
        val manager = alarmManager()
        if (canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarmIntent())
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarmIntent())
        }
    }

    private fun cancelAlarm() = alarmManager().cancel(alarmIntent())

    suspend fun clear() {
        WorkManager.getInstance(context).cancelUniqueWork(LOOP_WORK)
        schedulePeriodic(null)
        cancelAlarm()
        context.stopService(Intent(context, LiveMatchService::class.java))
    }

    private fun FollowedMatchEntity.isDue() =
        MatchEvents.isDue(kickoff?.let(Instant::ofEpochMilli), state(), now())

    private fun FollowedMatchEntity.state() = runCatching { MatchState.valueOf(lastState) }.getOrDefault(MatchState.SCHEDULED)

    private fun FollowedMatchEntity.lastSeen() = LastSeen(state(), lastHome, lastAway)

    private fun FollowedMatchEntity.kickoffOrNow(): Instant = kickoff?.let(Instant::ofEpochMilli) ?: now()

    private fun Match.toEntity(auto: Boolean, existing: FollowedMatchEntity?) = FollowedMatchEntity(
        eventId = id,
        competitionSlug = competitionSlug,
        homeId = home.team.id,
        homeName = home.team.shortName,
        awayId = away.team.id,
        awayName = away.team.shortName,
        kickoff = date?.toEpochMilli() ?: existing?.kickoff,
        lastState = status.state.name,
        lastHome = home.score,
        lastAway = away.score,
        auto = existing?.auto ?: auto,
        followedAt = existing?.followedAt ?: now().toEpochMilli(),
    )

    companion object {
        private const val TAG = "MatchWatcher"
        private val SOON: Duration = Duration.ofHours(6)
        const val PERIODIC_WORK = "match-watch-periodic"
        const val LOOP_WORK = "match-watch-loop"
    }
}
