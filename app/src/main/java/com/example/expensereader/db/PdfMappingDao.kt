package com.example.expensereader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensereader.model.PdfMapping

@Dao
interface PdfMappingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mapping: PdfMapping)

    @Query("SELECT name FROM pdf_mapping WHERE `key` = :key LIMIT 1")
    suspend fun getNameByKey(key: String): String?
}
