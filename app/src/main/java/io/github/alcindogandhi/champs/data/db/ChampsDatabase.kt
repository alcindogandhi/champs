package io.github.alcindogandhi.champs.data.db

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

@Database(entities = [SimulationEntity::class, SimulatedScoreEntity::class], version = 1)
abstract class ChampsDatabase : RoomDatabase() {
    abstract fun simulations(): SimulationDao
}
