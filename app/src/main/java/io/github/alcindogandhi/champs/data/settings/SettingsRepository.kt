package io.github.alcindogandhi.champs.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.domain.Team
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colunas opcionais da tabela de classificação (posição, time e pontos sempre aparecem). */
enum class TableColumn { PLAYED, WINS, DRAWS, LOSSES, GOALS_FOR, GOALS_AGAINST, GOAL_DIFFERENCE, PERCENTAGE }

data class FavoriteTeam(val competitionSlug: String, val team: Team)

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val followed: List<String> = CompetitionCatalog.defaultFollowed,
    val favorite: FavoriteTeam? = null,
    val columns: Set<TableColumn> = DEFAULT_COLUMNS,
) {
    companion object {
        val DEFAULT_COLUMNS = setOf(
            TableColumn.PLAYED, TableColumn.WINS, TableColumn.DRAWS, TableColumn.LOSSES, TableColumn.GOAL_DIFFERENCE,
        )
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            theme = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            followed = prefs[FOLLOWED]?.split(',')?.filter { CompetitionCatalog.find(it) != null }
                ?: CompetitionCatalog.defaultFollowed,
            favorite = favorite(prefs),
            columns = prefs[COLUMNS]?.split(',')?.mapNotNull { name ->
                runCatching { TableColumn.valueOf(name) }.getOrNull()
            }?.toSet() ?: Settings.DEFAULT_COLUMNS,
        )
    }.distinctUntilChanged()

    private fun favorite(prefs: Preferences): FavoriteTeam? {
        val slug = prefs[FAV_SLUG] ?: return null
        val id = prefs[FAV_ID] ?: return null
        val name = prefs[FAV_NAME] ?: return null
        return FavoriteTeam(slug, Team(id = id, name = name, shortName = name, logo = prefs[FAV_LOGO]))
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setFollowed(slugs: List<String>) {
        store.edit { it[FOLLOWED] = slugs.distinct().joinToString(",") }
    }

    suspend fun setFavorite(favorite: FavoriteTeam?) {
        store.edit { prefs ->
            if (favorite == null) {
                prefs.remove(FAV_SLUG); prefs.remove(FAV_ID); prefs.remove(FAV_NAME); prefs.remove(FAV_LOGO)
            } else {
                prefs[FAV_SLUG] = favorite.competitionSlug
                prefs[FAV_ID] = favorite.team.id
                prefs[FAV_NAME] = favorite.team.name
                favorite.team.logo?.let { prefs[FAV_LOGO] = it } ?: prefs.remove(FAV_LOGO)
            }
        }
    }

    suspend fun setColumns(columns: Set<TableColumn>) {
        store.edit { prefs -> prefs[COLUMNS] = TableColumn.entries.filter { it in columns }.joinToString(",") { it.name } }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FOLLOWED = stringPreferencesKey("followed")
        val COLUMNS = stringPreferencesKey("table_columns")
        val FAV_SLUG = stringPreferencesKey("favorite_slug")
        val FAV_ID = stringPreferencesKey("favorite_id")
        val FAV_NAME = stringPreferencesKey("favorite_name")
        val FAV_LOGO = stringPreferencesKey("favorite_logo")
    }
}
