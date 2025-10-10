package com.fjun.hassalarm.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PublishDao {
    @Query("SELECT * FROM publish ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Publish>>

    @Insert
    suspend fun insertAll(vararg publish: Publish)

    @Query("DELETE FROM publish")
    suspend fun deleteAll()
}