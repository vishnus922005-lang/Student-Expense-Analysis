// File: app/src/main/java/com/example/expensereader/db/AppDatabase.kt
package com.example.expensereader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.expensereader.model.AccountIdentity
import com.example.expensereader.model.Expense
import com.example.expensereader.model.PdfMapping
import com.example.expensereader.model.SkippedExpense

@Database(
    entities = [
        Expense::class,
        PdfMapping::class,
        AccountIdentity::class,
        CategoryBudget::class,
        SkippedExpense::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun pdfMappingDao(): PdfMappingDao
    abstract fun accountIdentityDao(): AccountIdentityDao
    abstract fun skippedExpenseDao(): SkippedExpenseDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ✅ Migration: add smsBody column to expenses table (keeps old data)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN smsBody TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // ✅ Migration: create skipped_expenses table
        // (Needed because you added SkippedExpense entity in version 3)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skipped_expenses (
                        expenseId INTEGER NOT NULL,
                        skippedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(expenseId)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eas_db"
                )
                    // ✅ Keep old data and migrate properly
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
