package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TunnelConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelConfigDao {
    @Query("SELECT * FROM tunnel_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<TunnelConfig>>

    @Query("SELECT * FROM tunnel_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Long): TunnelConfig?

    @Query("SELECT * FROM tunnel_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfig(): TunnelConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: TunnelConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<TunnelConfig>)

    @Update
    suspend fun updateConfig(config: TunnelConfig)

    @Delete
    suspend fun deleteConfig(config: TunnelConfig)

    @Query("UPDATE tunnel_configs SET isDefault = 0")
    suspend fun resetDefaults()

    @Query("UPDATE tunnel_configs SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM tunnel_configs")
    suspend fun getCount(): Int
}
