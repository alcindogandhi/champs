package io.github.alcindogandhi.champs.data.db

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Uma simulação de campeonato de pontos corridos. */
@Entity(tableName = "simulation", indices = [Index("competitionSlug")])
data class SimulationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionSlug: String,
    val name: String,
    val createdAt: Long,
)

/** Placar escolhido pelo usuário para um jogo ainda não encerrado. */
@Entity(
    tableName = "simulated_score",
    primaryKeys = ["simulationId", "matchId"],
    foreignKeys = [
        ForeignKey(
            entity = SimulationEntity::class,
            parentColumns = ["id"],
            childColumns = ["simulationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SimulatedScoreEntity(
    val simulationId: Long,
    val matchId: String,
    val homeGoals: Int,
    val awayGoals: Int,
)

@Dao
interface SimulationDao {
    @Query("SELECT * FROM simulation WHERE competitionSlug = :slug ORDER BY createdAt DESC")
    fun observeByCompetition(slug: String): Flow<List<SimulationEntity>>

    @Query("SELECT * FROM simulation WHERE id = :id")
    fun observe(id: Long): Flow<SimulationEntity?>

    @Insert
    suspend fun insert(simulation: SimulationEntity): Long

    @Query("UPDATE simulation SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM simulation WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM simulation")
    suspend fun deleteAll()

    @Query("SELECT * FROM simulated_score WHERE simulationId = :simulationId")
    fun observeScores(simulationId: Long): Flow<List<SimulatedScoreEntity>>

    @Upsert
    suspend fun upsertScore(score: SimulatedScoreEntity)

    @Query("DELETE FROM simulated_score WHERE simulationId = :simulationId AND matchId = :matchId")
    suspend fun deleteScore(simulationId: Long, matchId: String)

    @Query("DELETE FROM simulated_score WHERE simulationId = :simulationId")
    suspend fun clearScores(simulationId: Long)
}

/**
 * Jogo acompanhado para notificações, com o último estado visto (para detectar início e gols).
 * [auto] marca os jogos do time do coração, incluídos automaticamente.
 */
@Entity(tableName = "followed_match")
data class FollowedMatchEntity(
    @PrimaryKey val eventId: String,
    val competitionSlug: String,
    val homeId: String,
    val homeName: String,
    val awayId: String,
    val awayName: String,
    /** Horário de início, em milissegundos desde a época; null se a ESPN não informou. */
    val kickoff: Long?,
    /** Nome de [io.github.alcindogandhi.champs.domain.MatchState]. */
    val lastState: String,
    val lastHome: Int?,
    val lastAway: Int?,
    val auto: Boolean,
    val followedAt: Long,
)

@Dao
interface FollowedMatchDao {
    @Query("SELECT eventId FROM followed_match")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM followed_match WHERE lastState NOT IN ('FINISHED', 'CANCELED') ORDER BY kickoff")
    suspend fun pending(): List<FollowedMatchEntity>

    @Query("SELECT * FROM followed_match WHERE eventId = :eventId")
    suspend fun get(eventId: String): FollowedMatchEntity?

    @Upsert
    suspend fun upsert(match: FollowedMatchEntity)

    @Query("DELETE FROM followed_match WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("DELETE FROM followed_match WHERE auto = 1 AND lastState = 'SCHEDULED'")
    suspend fun deleteUpcomingAuto()

    /** Limpa jogos encerrados há tempo (ou que nunca aconteceram). */
    @Query("DELETE FROM followed_match WHERE kickoff IS NOT NULL AND kickoff < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(
    entities = [SimulationEntity::class, SimulatedScoreEntity::class, FollowedMatchEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ChampsDatabase : RoomDatabase() {
    abstract fun simulations(): SimulationDao
    abstract fun followedMatches(): FollowedMatchDao
}
