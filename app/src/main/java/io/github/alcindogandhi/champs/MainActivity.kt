package io.github.alcindogandhi.champs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.alcindogandhi.champs.ui.AppRoot
import io.github.alcindogandhi.champs.ui.MatchRoute
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Jogo a abrir quando a activity vem de uma notificação. */
    private val pendingMatch = MutableStateFlow<MatchRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        val container = (application as ChampsApp).container
        setContent { AppRoot(container, pendingMatch) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val slug = intent?.getStringExtra(EXTRA_SLUG) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        pendingMatch.value = MatchRoute(slug, eventId)
    }

    companion object {
        const val EXTRA_SLUG = "io.github.alcindogandhi.champs.SLUG"
        const val EXTRA_EVENT_ID = "io.github.alcindogandhi.champs.EVENT_ID"
    }
}
