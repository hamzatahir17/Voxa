package com.voxa.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "itinerary_items")
data class ItineraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val time: String,
    val title: String,
    val subtitle: String,
    val isCompleted: Boolean,
    val leadTimeMins: Int
)
