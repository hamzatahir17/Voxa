package com.voxa.app.data.local.dao

import androidx.room.*
import com.voxa.app.data.local.entity.ItineraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itinerary_items ORDER BY id ASC")
    fun getAllItems(): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itinerary_items ORDER BY id ASC")
    fun getAllItemsSync(): List<ItineraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItineraryEntity): Long

    @Update
    suspend fun updateItem(item: ItineraryEntity)

    @Delete
    suspend fun deleteItem(item: ItineraryEntity)

    @Query("DELETE FROM itinerary_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Int)
}
