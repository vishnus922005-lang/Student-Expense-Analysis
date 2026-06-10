package com.example.expensereader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensereader.model.SkippedExpense

@Dao
interface SkippedExpenseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: SkippedExpense)

    @Query("DELETE FROM skipped_expenses WHERE expenseId = :expenseId")
    suspend fun unskip(expenseId: Long)

    @Query("SELECT COUNT(*) FROM skipped_expenses")
    suspend fun countAll(): Int


}
