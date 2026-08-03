package com.example.expensereader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensereader.model.Expense
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete
import com.example.expensereader.model.DayTotal
import com.example.expensereader.model.ChartDayTotal
import com.example.expensereader.db.BudgetDayTotal

data class MerchantSummaryRow(
    val merchant: String,
    val total: Double,
    val txnCount: Int
)

data class MerchantTxnRow(
    val date: Long,
    val amount: Double
)

data class UnknownSmsDebugRow(
    val id: Long,
    val name: String?,
    val upiRef: String?,
    val merchantAcc: String?
)


data class UnknownAccGroup(
    val merchantAcc: String,
    val txnCount: Int,
    val total: Double
)

data class MerchantTotalRow(
    val merchant: String,
    val totalAmount: Double,
    val txnCount: Int
)

data class WeekendWeekdayRow(
    val isWeekend: Int,      
    val totalAmount: Double,
    val txnCount: Int
)

data class TxnLite(
    val date: Long,
    val amount: Double
)

data class MonthSummaryRow(
    val monthNo: Int,      
    val totalAmount: Double,
    val txnCount: Int
)

data class DayCount(
    val dayStart: Long,
    val cnt: Int
)

data class TodayUnknownSummary(
    val total: Double,
    val cnt: Int
)

data class LatestExpenseRow(
    val id: Long,
    val amount: Double
)

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Query("""
        SELECT merchantAcc FROM expenses
        WHERE source = 'SMS'
          AND (upiRef = :ref OR refNo = :ref)
          AND merchantAcc IS NOT NULL
          AND merchantAcc != ''
        LIMIT 1
    """)
    suspend fun findMerchantAccByRef(ref: String): String?

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            needsStatementImport = 0
        WHERE source = 'SMS'
          AND merchantAcc = :merchantAcc
          AND (
                TRIM(name) = ''
             OR name LIKE 'Unknown-%'
             OR name LIKE 'Unknown %'
          )
    """)
    suspend fun updateByMerchantAccIfUnknown(
        merchantAcc: String,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
          AND (
                :showUnknown = 1
                OR (
                    needsStatementImport = 0
                    AND TRIM(name) != ''
                    AND name NOT LIKE 'Unknown-%'
                    AND name NOT LIKE 'Unknown %'
                )
          )
        ORDER BY date DESC
    """)
    fun getHomeSmsExpensesFlow(showUnknown: Int): Flow<List<Expense>>

    @Query("""
        SELECT COUNT(*) FROM expenses
        WHERE source = 'SMS'
          AND needsStatementImport = 1
    """)
    fun getPendingImportCountFlow(): Flow<Int>

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            userEdited = 1,
            needsStatementImport = 0
        WHERE id = :id
    """)
    suspend fun updateNameCategory(id: Long, newName: String, newCategory: String): Int

    @Query("""
        SELECT COUNT(*) FROM expenses
        WHERE source = 'SMS'
          AND refNo = :refNo
          AND date = :date
          AND amount = :amount
    """)
    suspend fun existsSms(refNo: String, date: Long, amount: Double): Int

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            needsStatementImport = 0
        WHERE source = 'SMS'
          AND (upiRef = :ref OR refNo = :ref)
          AND (
                TRIM(name) = ''
             OR name LIKE 'Unknown-%'
             OR name LIKE 'Unknown %'
          )
    """)
    suspend fun updateByRefIfUnknown(ref: String, newName: String, newCategory: String): Int

    @Query("""
        UPDATE expenses
        SET name =
            CASE
                WHEN merchantAcc IS NOT NULL AND merchantAcc != '' THEN 'Unknown-' || merchantAcc
                WHEN accNo IS NOT NULL AND accNo != '' THEN 'Unknown-' || accNo
                ELSE 'Unknown'
            END
        WHERE source = 'SMS'
          AND needsStatementImport = 1
          AND (
                TRIM(name) = ''
             OR name LIKE 'Unknown-%'
             OR name LIKE 'Unknown %'
          )
    """)
    suspend fun fillUnknownNamesForPendingSms(): Int

    @Query("""
        UPDATE expenses
        SET name = (
            SELECT pm.name
            FROM pdf_mapping pm
            WHERE pm.upiRef = expenses.upiRef
              AND pm.name IS NOT NULL
              AND TRIM(pm.name) != ''
              AND LOWER(pm.name) != 'unknown'
              AND pm.name NOT LIKE 'Unknown%'
            LIMIT 1
        )
        WHERE (name IS NULL OR TRIM(name) = '' OR name LIKE 'Unknown%')
          AND upiRef IS NOT NULL AND upiRef != ''
          AND EXISTS (
              SELECT 1 FROM pdf_mapping pm
              WHERE pm.upiRef = expenses.upiRef
                AND pm.name IS NOT NULL
                AND TRIM(pm.name) != ''
                AND LOWER(pm.name) != 'unknown'
                AND pm.name NOT LIKE 'Unknown%'
          )
    """)
    suspend fun backfillUnknownNamesFromPdfMappingByUpiRef(): Int

    @Query("""
        SELECT id, name, upiRef, merchantAcc
        FROM expenses
        WHERE source='SMS'
          AND (name IS NULL OR name = '' OR name LIKE 'Unknown%')
        ORDER BY date DESC
        LIMIT 200
    """)
    suspend fun debugUnknownSms(): List<UnknownSmsDebugRow>

    @Query("""
        SELECT 
            name AS merchant,
            SUM(amount) AS total,
            COUNT(*) AS txnCount
        FROM expenses
        WHERE source IN ('SMS','MANUAL')
        AND needsStatementImport = 0
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        AND category = :category
        AND date BETWEEN :startMillis AND :endMillis
        GROUP BY name
        ORDER BY total DESC
    """)
    fun getMerchantsForCategoryInRange(
        category: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<MerchantSummaryRow>>

    @Query("""
        SELECT date, amount
        FROM expenses
        WHERE source IN ('SMS','MANUAL')
        AND needsStatementImport = 0
        AND category = :category
        AND name = :merchant
        AND date BETWEEN :startMillis AND :endMillis
        ORDER BY date DESC
    """)
    fun getMerchantTransactionsInRange(
        category: String,
        merchant: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<MerchantTxnRow>>

    @Query("""
        SELECT COALESCE(NULLIF(TRIM(category), ''), 'Others') AS name,
            SUM(amount) AS total,
            COUNT(*) AS txnCount
        FROM expenses
        WHERE needsStatementImport = 0
        AND date BETWEEN :fromMillis AND :toMillis
        GROUP BY COALESCE(NULLIF(TRIM(category), ''), 'Others')
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotalsBetween(fromMillis: Long, toMillis: Long): List<CategoryTotal>

    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE needsStatementImport = 0
        AND date BETWEEN :fromMillis AND :toMillis
    """)
    suspend fun getTotalSpendBetween(fromMillis: Long, toMillis: Long): Double

    @Query("""
        SELECT COUNT(*)
        FROM expenses
        WHERE needsStatementImport = 0
        AND date BETWEEN :fromMillis AND :toMillis
    """)
    suspend fun getTxnCountBetween(fromMillis: Long, toMillis: Long): Int

    @Query("SELECT MIN(date) FROM expenses")
    suspend fun getFirstExpenseDate(): Long?

    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
        AND needsStatementImport = 0
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        ORDER BY date DESC
        LIMIT 1000
    """)
    fun getRecentSmsFlow(): Flow<List<Expense>>

    @Query("""
        SELECT name FROM expenses
        WHERE source='SMS'
        AND merchantAcc = :merchantAcc
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun findKnownNameByMerchantAcc(merchantAcc: String): String?

    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
        AND needsStatementImport = 0
        AND date(date/1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        AND LOWER(name) != 'unknown'
        ORDER BY date DESC
    """)
    fun observeTodayKnownSms(): Flow<List<Expense>>
    
    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
        AND (
                TRIM(name) = ''
            OR name LIKE 'Unknown-%'
            OR name LIKE 'Unknown %'
            OR LOWER(name) = 'unknown'
        )
        ORDER BY date DESC
    """)
    fun observeUnknownSms(): Flow<List<Expense>>

        UPDATE expenses
        SET 
            name = (
                SELECT e2.name
                FROM expenses e2
                WHERE e2.source = 'SMS'
                AND e2.merchantAcc = expenses.merchantAcc
                AND e2.needsStatementImport = 0
                AND TRIM(e2.name) != ''
                AND e2.name NOT LIKE 'Unknown-%'
                AND e2.name NOT LIKE 'Unknown %'
                AND LOWER(e2.name) != 'unknown'
                ORDER BY e2.date DESC
                LIMIT 1
            ),
            category = (
                SELECT e2.category
                FROM expenses e2
                WHERE e2.source = 'SMS'
                AND e2.merchantAcc = expenses.merchantAcc
                AND e2.needsStatementImport = 0
                AND TRIM(e2.name) != ''
                AND e2.name NOT LIKE 'Unknown-%'
                AND e2.name NOT LIKE 'Unknown %'
                AND LOWER(e2.name) != 'unknown'
                ORDER BY e2.date DESC
                LIMIT 1
            ),
            needsStatementImport = 0
        WHERE expenses.source = 'SMS'
        AND expenses.merchantAcc IS NOT NULL
        AND expenses.merchantAcc != ''
        AND (
                TRIM(expenses.name) = ''
            OR expenses.name LIKE 'Unknown-%'
            OR expenses.name LIKE 'Unknown %'
            OR LOWER(expenses.name) = 'unknown'
        )
        AND EXISTS (
                SELECT 1
                FROM expenses e2
                WHERE e2.source = 'SMS'
                AND e2.merchantAcc = expenses.merchantAcc
                AND e2.needsStatementImport = 0
                AND TRIM(e2.name) != ''
                AND e2.name NOT LIKE 'Unknown-%'
                AND e2.name NOT LIKE 'Unknown %'
                AND LOWER(e2.name) != 'unknown'
        )
    """)
    suspend fun propagateResolvedNamesToSameMerchantAcc(): Int

    

    

    @Query("""
        UPDATE expenses
        SET name = (
            SELECT pm.name
            FROM pdf_mapping pm
            WHERE pm.accNo = expenses.accNo
            AND pm.name IS NOT NULL
            AND TRIM(pm.name) != ''
            AND LOWER(pm.name) != 'unknown'
            AND pm.name NOT LIKE 'Unknown%'
            LIMIT 1
        )
        WHERE (name IS NULL OR TRIM(name) = '' OR name LIKE 'Unknown%')
        AND accNo IS NOT NULL AND accNo != ''
        AND EXISTS (
            SELECT 1 FROM pdf_mapping pm
            WHERE pm.accNo = expenses.accNo
                AND pm.name IS NOT NULL
                AND TRIM(pm.name) != ''
                AND LOWER(pm.name) != 'unknown'
                AND pm.name NOT LIKE 'Unknown%'
        )
    """)
    suspend fun backfillUnknownNamesFromPdfMappingByAccNo(): Int

    @Query("""
        UPDATE expenses
        SET accNo = :myAcc4,
            merchantAcc = :merchant4
        WHERE source = 'SMS'
        AND refNo = :refNo
        AND date = :date
        AND amount = :amount
    """)
    suspend fun updateAccsForExistingSms(
        refNo: String,
        date: Long,
        amount: Double,
        myAcc4: String?,
        merchant4: String?
    ): Int

    @Query("""
        WITH uniq AS (
            SELECT 
                merchantAcc,
                MAX(name) AS name,
                MAX(category) AS category
            FROM expenses
            WHERE source = 'SMS'
            AND merchantAcc IS NOT NULL AND merchantAcc != ''
            AND TRIM(name) != ''
            AND name NOT LIKE 'Unknown-%'
            AND name NOT LIKE 'Unknown %'
            AND LOWER(name) != 'unknown'
            GROUP BY merchantAcc
            HAVING COUNT(DISTINCT name) = 1
        )
        UPDATE expenses
        SET name = (SELECT uniq.name FROM uniq WHERE uniq.merchantAcc = expenses.merchantAcc),
            category = (SELECT uniq.category FROM uniq WHERE uniq.merchantAcc = expenses.merchantAcc),
            needsStatementImport = 0
        WHERE source = 'SMS'
        AND merchantAcc IS NOT NULL AND merchantAcc != ''
        AND merchantAcc IN (SELECT merchantAcc FROM uniq)
        AND (
                TRIM(name) = ''
            OR name LIKE 'Unknown-%'
            OR name LIKE 'Unknown %'
            OR LOWER(name) = 'unknown'
        )
    """)
    suspend fun propagateResolvedNamesToSameMerchantAccSafe(): Int

    data class MatchedSmsLogRow(
        val id: Long,
        val name: String,
        val amount: Double,
        val date: Long,
        val accNo: String?,       
        val merchantAcc: String?,  
        val refNo: String,
        val upiRef: String?
    )

    @Query("""
        SELECT id, name, amount, date, accNo, merchantAcc, refNo, upiRef
        FROM expenses
        WHERE source = 'SMS'
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown%'
        AND LOWER(name) != 'unknown'
        ORDER BY date DESC
    """)
    suspend fun getMatchedSmsForLog(): List<MatchedSmsLogRow>

    @Query("""
        SELECT COUNT(DISTINCT name)
        FROM expenses
        WHERE source='SMS'
        AND merchantAcc = :merchantAcc
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        AND LOWER(name) != 'unknown'
    """)
    suspend fun countDistinctKnownNamesForMerchantAcc(merchantAcc: String): Int

    @Query("""
        UPDATE expenses
        SET 
            name = :newName,
            category = :newCategory,
            needsStatementImport = 0
        WHERE source = 'SMS'
        AND merchantAcc = :merchantAcc
        AND userEdited = 0
    """)
    suspend fun forceUpdateByMerchantAccFromUser(
        merchantAcc: String,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            needsStatementImport = 0
        WHERE source = 'SMS'
        AND merchantAcc = :merchantAcc
        AND (
                TRIM(name) = ''
            OR name LIKE 'Unknown-%'
            OR name LIKE 'Unknown %'
            OR LOWER(name) = 'unknown'
        )
        AND userEdited = 0
    """)
    suspend fun updateUnknownOnlyByMerchantAcc(
        merchantAcc: String,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
        SELECT DISTINCT name
        FROM expenses
        WHERE source = 'SMS'
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown%'
        AND LOWER(name) != 'unknown'
        ORDER BY name
    """)
    suspend fun getAllKnownNamesForSuggest(): List<String>    

    @Query("""
        UPDATE expenses
        SET name = :newName, category = :newCategory
        WHERE merchantAcc = :merchantAcc
    """)
    suspend fun updateAllByMerchantAcc(
        merchantAcc: String,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
        UPDATE expenses
        SET name = :newName, category = :newCategory
        WHERE accNo = :accNo
    """)
    suspend fun updateAllByAccNo(
        accNo: String,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory
        WHERE source='SMS'
        AND merchantAcc = :merchantAcc
        AND (name='Unknown' OR name LIKE 'Unknown-%')
    """)
    suspend fun updateUnknownByMerchantAcc(
        merchantAcc: String,
        newName: String,
        newCategory: String
    ): Int
    
    @Query("""
        SELECT DISTINCT category
        FROM expenses
        WHERE name = :name
        AND category IS NOT NULL AND category != ''
        ORDER BY category COLLATE NOCASE
    """)
    suspend fun getCategoriesByName(name: String): List<String>

    @Query("""
        SELECT 
            merchantAcc AS merchantAcc,
            COUNT(*) AS txnCount,
            SUM(amount) AS total
        FROM expenses
        WHERE source = 'SMS'
        AND (name IS NULL OR name = '' OR name LIKE 'Unknown%')
        AND merchantAcc IS NOT NULL AND merchantAcc != ''
        GROUP BY merchantAcc
        ORDER BY txnCount DESC
    """)
    fun getUnknownAccGroupsFlow(): kotlinx.coroutines.flow.Flow<List<UnknownAccGroup>>

    @Query("""
        SELECT * FROM expenses
        WHERE (LOWER(name) LIKE 'unknown%')
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getUnknownSmsOnce(limit: Int = 200): List<Expense>

    @Query("""
        SELECT DISTINCT name
        FROM expenses
        WHERE merchantAcc = :merchantAcc
        AND name IS NOT NULL
        AND TRIM(name) != ''
        AND LOWER(name) NOT LIKE 'unknown%'
    """)
    suspend fun getKnownNamesByMerchantAcc(merchantAcc: String): List<String>

    @Query("""
        SELECT category
        FROM expenses
        WHERE name = :name
        AND category IS NOT NULL
        AND TRIM(category) != ''
        AND LOWER(category) NOT LIKE 'uncategorized%'
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun getLastCategoryForName(name: String): String?

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            needsStatementImport = 0
        WHERE id = :id
    """)
    suspend fun updateNameCategoryAndResolve(id: Long, newName: String, newCategory: String): Int

    @Query("UPDATE expenses SET needsStatementImport = 0 WHERE id = :id")
    suspend fun clearNeedsStatementImport(id: Long)
        data class PendingAccRow(
        val id: Long,
        val merchantAcc: String
    )

    @Query("""
        SELECT id, merchantAcc FROM expenses
        WHERE source = 'SMS'
        AND merchantAcc IS NOT NULL AND TRIM(merchantAcc) != ''
        AND (
            needsStatementImport = 1
            OR TRIM(name) = ''
            OR name LIKE 'Unknown-%'
            OR name LIKE 'Unknown %'
            )
        """)
    suspend fun getPendingUnknownWithAcc(): List<PendingAccRow>

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            userEdited = 0,
            needsStatementImport = 0
        WHERE id = :id
    """)
    suspend fun updateNameCategoryAuto(id: Long, newName: String, newCategory: String): Int

    
    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
        AND (
                needsStatementImport = 1
            OR TRIM(name) = ''
            OR name LIKE 'Unknown-%'
            OR name LIKE 'Unknown %'
        )
        ORDER BY date DESC
        LIMIT 10
    """)
    suspend fun getUnknownSmsOnce(): List<Expense>

    @Query("""
        SELECT * FROM expenses
        WHERE source='SMS'
        AND needsStatementImport = 0
        AND TRIM(name) != ''
        AND name NOT LIKE 'Unknown-%'
        AND name NOT LIKE 'Unknown %'
        ORDER BY date DESC
        LIMIT 500
    """)
    suspend fun getRecentlyResolvedSmsForCategory(): List<Expense>

   
    @Query("""
    UPDATE expenses
    SET category = :cat
    WHERE id = :id
    """)
    suspend fun updateCategoryOnly(id: Long, cat: String): Int

    @Query("""
    UPDATE expenses
    SET category = :newCategory,
        name = CASE 
            WHEN (name IS NULL OR name = '' OR LOWER(name) LIKE 'unknown%') THEN :newName
            ELSE name
        END
    WHERE refNo = :refNo AND date = :date AND amount = :amount
    """)
    suspend fun updateNameCategoryForExistingSms(
        refNo: String,
        date: Long,
        amount: Double,
        newName: String,
        newCategory: String
    ): Int

    @Query("""
    SELECT * FROM expenses
    WHERE (category IS NULL OR category = '' OR category = 'Uncategorized')
    AND name IS NOT NULL
    AND name != ''
    AND LOWER(name) NOT LIKE 'unknown%'
    """)
    suspend fun getUncategorizedNonUnknown(): List<Expense>

    
    @Query("""
        UPDATE expenses
        SET category = 'Others'
        WHERE (
            name = 'Unknown'
            OR name LIKE 'Unknown-%'
            OR name LIKE 'unknown%'
        )
        AND (category = 'Uncategorized' OR category IS NULL OR category = '')
    """)
    suspend fun fixUnknownCategoryToOthers(): Int

    // A small projection (create this near other DAO result classes)
    data class IdNameCatRow(
        val id: Long,
        val name: String?,
        val category: String?
    )

    @Query("""
        SELECT id, name, category
        FROM expenses
        WHERE userEdited = 0
        AND name IS NOT NULL
        AND TRIM(name) != ''
        AND LOWER(name) NOT LIKE 'unknown%'
        AND (
                category IS NULL
            OR TRIM(category) = ''
            OR category = 'Uncategorized'
            OR category = 'Others'
        )
    """)
    suspend fun getAutoRowsNeedingCategory(): List<IdNameCatRow>

    @Query("""
        UPDATE expenses
        SET category = :cat
        WHERE id = :id
    """)
    suspend fun updateCategoryById(id: Long, cat: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE needsStatementImport = 1")
        suspend fun getPendingImportCount(): Int

        @Query("""
        SELECT IFNULL(SUM(amount), 0)
        FROM expenses
        WHERE date >= :startOfDay
        AND name NOT LIKE 'unknown%'
    """)
    suspend fun getTodayTotal(startOfDay: Long): Double

    @Query("""
        SELECT COUNT(*)
        FROM expenses
        WHERE date >= :startOfDay
        AND name NOT LIKE 'unknown%'
    """)
    suspend fun getTodayTxnCount(startOfDay: Long): Int

    @Query("""
        SELECT *
        FROM expenses
        WHERE needsStatementImport = 0
        AND name IS NOT NULL
        AND TRIM(name) != ''
        AND LOWER(name) NOT LIKE 'unknown%'
        AND date >= :startOfDay
        ORDER BY date DESC
    """)
    fun getRecentSmsTodayFlow(startOfDay: Long): kotlinx.coroutines.flow.Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses
        WHERE source = 'SMS'
        AND (
                needsStatementImport = 1
            OR TRIM(name) = ''
            OR LOWER(name) LIKE 'unknown%'
        )
        ORDER BY date DESC
        LIMIT 10
    """)
    fun getUnknownSmsFlow(): Flow<List<Expense>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(expense: Expense): Int

    @Query("SELECT * FROM category_budgets")
    suspend fun getAllBudgets(): List<CategoryBudget>

    @Query("SELECT * FROM category_budgets")
    suspend fun getAllCategoryBudgets(): List<CategoryBudget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryBudget(budget: CategoryBudget)

    @Query("""
        SELECT *
        FROM expenses
        WHERE date BETWEEN :startMillis AND :endMillis
        AND (
            name IS NULL OR TRIM(name) = '' OR LOWER(name) LIKE 'unknown%'
        )
        ORDER BY date DESC
    """)
    fun getUnknownSmsExpensesInRange(
        startMillis: Long,
        endMillis: Long
    ): kotlinx.coroutines.flow.Flow<List<com.example.expensereader.model.Expense>>

    

    @Query("""
        SELECT category AS category,
               IFNULL(SUM(amount), 0) AS totalAmount,
               COUNT(*) AS txnCount
        FROM expenses
        WHERE date >= :startMillis AND date < :endMillis
        GROUP BY category
    """)
    suspend fun getCategorySummaryBetween(
        startMillis: Long,
        endMillis: Long
    ): List<CategorySummaryRow>

    @Query("""
        SELECT 
            strftime('%Y-%m-%d', datetime(date/1000, 'unixepoch', 'localtime')) AS day,
            SUM(amount) AS totalAmount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getDailyTotalsBetween(fromMillis: Long, toMillis: Long): List<DayTotal>

    

    @Query("""
        SELECT DISTINCT COALESCE(NULLIF(TRIM(category), ''), 'Others') AS category
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        ORDER BY category ASC
    """)
    suspend fun getCategoriesBetween(fromMillis: Long, toMillis: Long): List<String>


   

    @Query("""
        SELECT 
            COALESCE(NULLIF(TRIM(name), ''), 'Unknown') AS merchant,
            SUM(amount) AS totalAmount,
            COUNT(*) AS txnCount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        AND COALESCE(NULLIF(TRIM(category), ''), 'Others') = :category
        GROUP BY merchant
        ORDER BY totalAmount DESC
        LIMIT :limit
    """)
    suspend fun getTopMerchantsForCategoryBetween(
        fromMillis: Long,
        toMillis: Long,
        category: String,
        limit: Int = 10
    ): List<MerchantTotalRow>

    @Query("""
        SELECT (date / 86400000) * 86400000 AS dayStart,
            SUM(amount) AS total
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        GROUP BY dayStart
        ORDER BY dayStart ASC
    """)
    suspend fun getDayWiseTotalsBetween(
        fromMillis: Long,
        toMillis: Long
    ): List<ChartDayTotal>

    @Query("""
        SELECT name as merchant, SUM(amount) as total, COUNT(*) as txnCount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        AND name IS NOT NULL AND TRIM(name) != '' AND LOWER(name) NOT LIKE 'unknown%'
        GROUP BY merchant
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun getTopMerchantBetween(fromMillis: Long, toMillis: Long): MerchantSummaryRow

    @Query("""
        SELECT
        CASE 
            WHEN strftime('%w', datetime(date/1000, 'unixepoch', 'localtime')) IN ('0','6')
            THEN 1 ELSE 0 
        END AS isWeekend,
        SUM(amount) AS totalAmount,
        COUNT(*) AS txnCount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        GROUP BY isWeekend
    """)
    suspend fun getWeekendWeekdaySummaryBetween(fromMillis: Long, toMillis: Long): List<WeekendWeekdayRow>

    @Query("""
        SELECT date, amount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
    """)
    suspend fun getTxnsBetween(fromMillis: Long, toMillis: Long): List<TxnLite>

    @Query("""
        SELECT 
            CAST(strftime('%m', datetime(date/1000, 'unixepoch', 'localtime')) AS INTEGER) AS monthNo,
            SUM(amount) AS totalAmount,
            COUNT(*) AS txnCount
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        GROUP BY monthNo
        ORDER BY monthNo
    """)
    suspend fun getMonthSummaryBetween(fromMillis: Long, toMillis: Long): List<MonthSummaryRow>

    @Query("""
        SELECT COUNT(*)
        FROM expenses
        WHERE LOWER(smsBody) LIKE '%autopay%'
        OR LOWER(smsBody) LIKE '%safe gold%'
        OR LOWER(smsBody) LIKE '%gold%'
        OR LOWER(smsBody) LIKE '%scheduled%'
        OR LOWER(smsBody) LIKE '%save%'
        OR LOWER(smsBody) LIKE '%saving%'
        OR LOWER(smsBody) LIKE '%sip%'
        OR LOWER(smsBody) LIKE '%systematic%'
        OR LOWER(smsBody) LIKE '%mf sip%'
        OR LOWER(smsBody) LIKE '%units allotted%'
        OR LOWER(smsBody) LIKE '%nav%'
        OR LOWER(smsBody) LIKE '%recurring%'
        OR LOWER(smsBody) LIKE '%rd%'
        OR LOWER(smsBody) LIKE '%installment%'
        OR LOWER(smsBody) LIKE '%mutual fund%'
        OR LOWER(smsBody) LIKE '%mf%'
        OR LOWER(smsBody) LIKE '%units%'
        OR LOWER(smsBody) LIKE '%folio%'
        OR LOWER(smsBody) LIKE '%digital gold%'
        OR LOWER(smsBody) LIKE '%safe gold%'
        OR LOWER(smsBody) LIKE '%augmont%'
        OR LOWER(smsBody) LIKE '%mmtc%'
        OR LOWER(smsBody) LIKE '%pamp%'
        OR LOWER(smsBody) LIKE '%24k%'
        OR LOWER(smsBody) LIKE '%grams%'
        OR LOWER(smsBody) LIKE '%gm%'
        OR LOWER(smsBody) LIKE '%gold purchased%'
        OR LOWER(smsBody) LIKE '%invest%'
        
    """)
    suspend fun getSavingSmsCount(): Int

    @Query("""
        SELECT (date / 86400000) * 86400000 AS dayStart,
            COUNT(*) AS cnt
        FROM expenses
        WHERE category = 'Savings'
        AND date BETWEEN :fromMillis AND :toMillis
        GROUP BY dayStart
        """)
    suspend fun getSavingCountsByDay(
        fromMillis: Long,
        toMillis: Long
    ): List<DayCount>

    @Query("""
        SELECT (((date + :tzOffset) / 86400000) * 86400000 - :tzOffset) AS dayStart,
            COUNT(*) AS cnt
        FROM expenses
        WHERE LOWER(category) = 'savings'
        AND date BETWEEN :fromMillis AND :toMillis
        GROUP BY dayStart
    """)
    suspend fun getSavingCountsByDay(
        fromMillis: Long,
        toMillis: Long,
        tzOffset: Long
    ): List<DayCount>

    
   @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE date >= :startOfDay
        AND LOWER(COALESCE(name, '')) NOT LIKE 'unknown%'
    """)
    suspend fun getTodayTotalExcludingUnknown(startOfDay: Long): Double

    @Query("""
        SELECT COUNT(*)
        FROM expenses
        WHERE date >= :startOfDay
        AND LOWER(COALESCE(name, '')) NOT LIKE 'unknown%'
    """)
    suspend fun getTodayTxnCountExcludingUnknown(startOfDay: Long): Int
    
    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE date >= :startOfDay
    """)
    suspend fun getTodayTotalAll(startOfDay: Long): Double

    @Query("""
        SELECT COUNT(*)
        FROM expenses
        WHERE date >= :startOfDay
    """)
    suspend fun getTodayTxnCountAll(startOfDay: Long): Int

    @Query("""
        SELECT 
            COALESCE(SUM(amount), 0) AS total,
            COUNT(*) AS cnt
        FROM expenses
        WHERE date >= :startOfDay
        AND (
                needsStatementImport = 1
            OR TRIM(COALESCE(name,'')) = ''
            OR LOWER(COALESCE(name,'')) LIKE 'unknown%'
        )
    """)
    suspend fun getTodayUnknownSummary(startOfDay: Long): TodayUnknownSummary

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
    """)
    suspend fun getTotalSavedAmountOrZero(): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
        AND date BETWEEN :fromMillis AND :toMillis
    """)
    suspend fun getSavedAmountBetweenOrZero(fromMillis: Long, toMillis: Long): Double

    @Query("""
        SELECT MIN((date / 86400000) * 86400000)
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
    """)
    suspend fun getFirstSavedDayStartOrNull(): Long?

    @Query("""
        SELECT COUNT(DISTINCT (date / 86400000))
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
        AND date BETWEEN :fromDayStart AND (:toDayStart + 86399999)
    """)
    suspend fun getDistinctSavedDaysCountBetween(fromDayStart: Long, toDayStart: Long): Int

    @Query("""
        SELECT MIN(date)
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
    """)
    suspend fun getFirstSavingMillisOrNull(): Long?

    @Query("""
        SELECT COUNT(DISTINCT ((date + :tzOffset) / 86400000))
        FROM expenses
        WHERE category IN ('Saving','SAVING','Savings','SAVINGS')
        AND date BETWEEN :fromMillis AND :toMillis
    """)
    suspend fun getDistinctSavingDaysCountBetween(
        fromMillis: Long,
        toMillis: Long,
        tzOffset: Long
    ): Int

    
    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) " +
        "FROM expenses " +
        "WHERE date >= :fromMillis"
    )
    suspend fun getTotalFrom(fromMillis: Long): Double

    @Query("""
        SELECT 
            (e.date / 86400000) * 86400000 AS dayStartMillis,
            COALESCE(SUM(e.amount), 0) AS totalAmount
        FROM expenses e
        WHERE e.date >= :startMillis
        AND e.id NOT IN (SELECT expenseId FROM skipped_expenses)
        GROUP BY dayStartMillis
        ORDER BY dayStartMillis ASC
    """)
    suspend fun getBudgetDailyTotalsFrom(startMillis: Long): List<com.example.expensereader.db.BudgetDayTotal>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSkippedExpense(row: com.example.expensereader.model.SkippedExpense)

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0.0)
        FROM expenses e
        LEFT JOIN skipped_expenses s ON s.expenseId = e.id
        WHERE e.date >= :startMillis
        AND e.type = 'DEBIT'
        AND s.expenseId IS NULL
    """)
    suspend fun getTodayTotalExcludingSkipped(startMillis: Long): Double

    @Query("""
        SELECT e.id AS id, e.amount AS amount
        FROM expenses e
        LEFT JOIN skipped_expenses s ON s.expenseId = e.id
        WHERE e.date >= :startMillis
        AND e.type = 'DEBIT'
        AND s.expenseId IS NULL
        ORDER BY e.date DESC
        LIMIT 1
    """)
    suspend fun getLatestExpenseTodayForSkip(startMillis: Long): LatestExpenseRow?

    @Query("""
        SELECT COALESCE(SUM(amount),0) 
        FROM expenses
        WHERE date >= :fromMillis
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
    """)
    suspend fun getTotalFromExcludingSkipped(fromMillis: Long): Double

    @Query("""
        SELECT COALESCE(SUM(amount),0) 
        FROM expenses
        WHERE date >= :startMillis
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
    """)
    suspend fun getTodayTotalAllExcludingSkipped(startMillis: Long): Double

    @Query("""
        SELECT e.id AS id, e.amount AS amount
        FROM expenses e
        WHERE e.date >= :startMillis
        AND e.id NOT IN (SELECT expenseId FROM skipped_expenses)
        ORDER BY e.date DESC
        LIMIT 1
    """)
    suspend fun getLatestExpenseTodayExcludingSkipped(startMillis: Long): LatestExpenseRow?

    @Query("""
        SELECT e.id AS id, e.amount AS amount
        FROM expenses e
        LEFT JOIN skipped_expenses s ON s.expenseId = e.id
        WHERE e.date >= :fromMillis
        AND e.type = 'DEBIT'
        AND s.expenseId IS NULL
        ORDER BY e.amount DESC, e.date DESC
        LIMIT 1
    """)
    suspend fun getMaxExpenseSinceExcludingSkipped(fromMillis: Long): LatestExpenseRow?


    @Query("""
        INSERT OR IGNORE INTO skipped_expenses(expenseId)
        SELECT e.id
        FROM expenses e
        LEFT JOIN skipped_expenses s ON s.expenseId = e.id
        WHERE e.date >= :fromMillis
        AND e.amount >= :minAmount
        AND e.amount > 0
        AND s.expenseId IS NULL
    """)
    suspend fun skipAllExpensesAboveAmount(fromMillis: Long, minAmount: Double): Long

    @Query("""
        INSERT OR IGNORE INTO skipped_expenses (expenseId)
        SELECT id
        FROM expenses
        WHERE date >= :startOfDay
        AND amount >= :minAmount
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
    """)
    suspend fun skipAllTodayAboveAmount(
        startOfDay: Long,
        minAmount: Double
    ): Unit

    @Query("""
        INSERT OR IGNORE INTO skipped_expenses (expenseId)
        SELECT id
        FROM expenses
        WHERE date >= :startOfDay
        AND amount >= :minAmount
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
    """)
    suspend fun autoSkipTodayAbove(
        startOfDay: Long,
        minAmount: Double
    ): Unit

    @Query("""
        SELECT *
        FROM expenses
        WHERE date >= :startMillis
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
        ORDER BY amount DESC
        LIMIT 1
    """)
    suspend fun getMaxExpenseTodayEntityForSkip(startMillis: Long): com.example.expensereader.model.Expense?

    @Query("""
        SELECT *
        FROM expenses
        WHERE date >= :startMillis
        AND id NOT IN (SELECT expenseId FROM skipped_expenses)
        ORDER BY amount DESC
        LIMIT 1
    """)
    suspend fun getMaxExpenseTodayEntity(startMillis: Long): Expense?

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            userEdited = 1,
            needsStatementImport = 0
        WHERE date BETWEEN :startMillis AND :endMillis
        AND LOWER(TRIM(name)) = LOWER(TRIM(:oldMerchantName))
    """)
    suspend fun bulkUpdateMerchantByNameInRange(
        oldMerchantName: String,
        newName: String,
        newCategory: String,
        startMillis: Long,
        endMillis: Long
    ): Int

    @Query("""
        UPDATE expenses
        SET name = :newName,
            category = :newCategory,
            userEdited = 1,
            needsStatementImport = 0
        WHERE date BETWEEN :startMillis AND :endMillis
        AND merchantAcc = :merchantAcc
    """)
    suspend fun bulkUpdateMerchantByAccInRange(
        merchantAcc: String,
        newName: String,
        newCategory: String,
        startMillis: Long,
        endMillis: Long
    ): Int

    @Query("""
        SELECT merchantAcc
        FROM expenses
        WHERE date BETWEEN :startMillis AND :endMillis
        AND LOWER(TRIM(name)) = LOWER(TRIM(:merchantName))
        AND merchantAcc IS NOT NULL
        AND TRIM(merchantAcc) != ''
        LIMIT 1
    """)
    suspend fun getAnyMerchantAccForNameInRange(
        merchantName: String,
        startMillis: Long,
        endMillis: Long
    ): String?

    @Query("""
        SELECT 
            (date / 86400000) * 86400000 AS dayStart,
            SUM(amount) as total
        FROM expenses
        WHERE date BETWEEN :weekStart AND :weekEnd
        GROUP BY dayStart
    """)
    fun observeDailyTotalsBetween(
        weekStart: Long,
        weekEnd: Long
    ): kotlinx.coroutines.flow.Flow<List<DailyTotalRow>>


    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date BETWEEN :start AND :end AND type='DEBIT'")
    suspend fun getTotalDebitBetween(start: Long, end: Long): Double

    @Query("""
        SELECT 
            (date / 86400000) * 86400000 AS dayStart,
            SUM(amount) AS total
        FROM expenses
        WHERE date BETWEEN :fromMillis AND :toMillis
        AND category = :category
        GROUP BY dayStart
        ORDER BY dayStart
    """)
    suspend fun getDayWiseTotalsForCategoryBetween(
        fromMillis: Long,
        toMillis: Long,
        category: String
    ): List<ChartDayTotal>

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :startDate")
    suspend fun getTotalAfterDate(startDate: Long): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE date BETWEEN :start AND :end")
    suspend fun getTotalBetween(start: Long, end: Long): Double?

    @Query("SELECT SUM(amount) FROM expenses")
    suspend fun getTotalExpenses(): Double?

    @Query("""
        SELECT 
            COALESCE(NULLIF(TRIM(category), ''), 'Others') AS name,
            SUM(amount) AS total,
            COUNT(*) AS txnCount
        FROM expenses
        GROUP BY COALESCE(NULLIF(TRIM(category), ''), 'Others')
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotals(): List<CategoryTotal>


    @Query("""
        SELECT SUM(amount) FROM expenses
        WHERE strftime('%m', date/1000, 'unixepoch') = strftime('%m', 'now')
    """)
    suspend fun getThisMonthTotal(): Double?

}
