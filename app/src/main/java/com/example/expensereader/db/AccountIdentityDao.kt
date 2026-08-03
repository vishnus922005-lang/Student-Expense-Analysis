package com.example.expensereader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensereader.model.AccountIdentity   

@Dao
interface AccountIdentityDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(identity: AccountIdentity): Long

    @Query("SELECT id FROM account_identity WHERE accNo = :accNo LIMIT 1")
    suspend fun findIdByAccNo(accNo: String): Long?

    @Query("SELECT name FROM account_identity WHERE id = :id LIMIT 1")
    suspend fun findNameById(id: Long): String?
}
