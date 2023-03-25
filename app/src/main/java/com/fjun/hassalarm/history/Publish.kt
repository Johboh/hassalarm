package com.fjun.hassalarm.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Publish(
    val timestamp: Long,
    val successful: Boolean,
    val triggerTimestamp: Long? = null,
    val errorMessage: String? = null,
    val creatorPackage: String? = null,
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
