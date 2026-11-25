package io.github.alcindogandhi.champs.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.alcindogandhi.champs.MainActivity
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.db.FollowedMatchEntity
import io.github.alcindogandhi.champs.domain.MatchEvent

/** Canais e montagem das notificações de jogos. */
class MatchNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.channel_events_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_events_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCHING, context.getString(R.string.channel_watching_name), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = context.getString(R.string.channel_watching_description)
                    setShowBadge(false)
                },
        )
    }

    /** Notificações permitidas pelo usuário (no Android 13+ é uma permissão em tempo de execução). */
    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    fun show(match: FollowedMatchEntity, event: MatchEvent) {
        if (!canNotify()) return
        val competition = CompetitionCatalog.get(match.competitionSlug).name
        val (title, text, id) = when (event) {
            MatchEvent.Started -> Triple(
                context.getString(R.string.notification_match_start),
                "${match.homeName} × ${match.awayName}",
                0,
            )
            is MatchEvent.GoalScored -> {
                val team = if (event.teamId == match.homeId) match.homeName else match.awayName
                val score = "${match.homeName} ${event.home} × ${event.away} ${match.awayName}"
                val detail = event.goal?.let { goal ->
                    val kind = when {
                        goal.ownGoal -> " (${context.getString(R.string.notification_own_goal)})"
                        goal.penalty -> " (${context.getString(R.string.notification_penalty)})"
                        else -> ""
                    }
                    listOfNotNull(goal.clock.ifBlank { null }, goal.scorer?.let { it + kind }).joinToString(" ")
                }
                Triple(
                    context.getString(R.string.notification_goal, team),
                    listOfNotNull(score, detail?.ifBlank { null }).joinToString(" · "),
                    // Um aviso por placar: dois gols seguidos não se sobrepõem.
                    1 + event.home * 100 + event.away,
                )
            }
        }
        val open = openMatch(match.competitionSlug, match.eventId)
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(competition)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(match.eventId)
            .setContentIntent(open)
            .build()
        // Resumo do grupo do jogo: sem ele, o Android agrupa sozinho e o toque no grupo só abre o app.
        val summary = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${match.homeName} × ${match.awayName}")
            .setSubText(competition)
            .setGroup(match.eventId)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(match.eventId, id, notification)
        notify(match.eventId, SUMMARY_ID, summary)
    }

    /** Notificação fixa do serviço que acompanha os jogos em andamento. */
    fun watching(matches: List<FollowedMatchEntity>): Notification {
        val lines = matches.map { m ->
            if (m.lastHome != null && m.lastAway != null) "${m.homeName} ${m.lastHome} × ${m.lastAway} ${m.awayName}" else "${m.homeName} × ${m.awayName}"
        }
        val count = matches.size.coerceAtLeast(1)
        return NotificationCompat.Builder(context, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_watching, count, count))
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp())
            .build()
    }

    @Suppress("MissingPermission") // verificado em canNotify()
    private fun notify(tag: String, id: Int, notification: Notification) {
        if (canNotify()) manager.notify(tag, id, notification)
    }

    private fun openMatch(slug: String, eventId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_SLUG, slug)
            .putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        return PendingIntent.getActivity(context, eventId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_EVENTS = "match_events"
        const val CHANNEL_WATCHING = "match_watching"
        const val WATCHING_ID = 1
        private const val SUMMARY_ID = -1
    }
}
