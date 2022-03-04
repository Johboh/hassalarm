package com.fjun.hassalarm.history

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PublishDao {
    @Query("SELECT * FROM publish ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<Publish>>

    @Insert
    fun insertAll(vararg publish: Publish)

    @Query("DELETE FROM publish")
    fun deleteAll()
}