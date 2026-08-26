package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_info")
data class GlobalInfoEntity(
    @PrimaryKey val id: String = "info",
    val aboutTitle: String = "",
    val aboutDescription: String = "",
    val aboutServices: String = "",
    val aboutFooter: String = "",
    val contactTitle: String = "",
    val contactPhone: String = ""
)
