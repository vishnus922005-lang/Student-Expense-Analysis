// File: app/src/main/java/com/example/expensereader/ui/home/HomeFragment.kt
package com.example.expensereader.ui.home

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.expensereader.util.BudgetManager
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.db.ExpenseDao
import com.example.expensereader.importer.BankPdfParser
import com.example.expensereader.ml.InsightModel
import com.example.expensereader.importer.StatementFromPdfSaver
import com.example.expensereader.model.Expense
import com.example.expensereader.sms.SmsInboxObserver
import com.example.expensereader.sms.SmsReader
import com.example.expensereader.util.SmartSavingPrefs
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.expensereader.ml.InsightSeverity
import android.widget.ImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.os.bundleOf
import com.example.expensereader.repo.InsightRepository


class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var tvTodaySpend: TextView
    private lateinit var tvTxnCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvPending: TextView

    
    private var budgetBinder: com.example.expensereader.ui.budget.BudgetCardBinder? = null

    private lateinit var insightRepository: InsightRepository


    private lateinit var badgeImport: TextView
    private lateinit var badgeTitle: TextView

    private lateinit var tvSavingStatus: TextView // ✅ NEW


    private val weekRepo = com.example.expensereader.repo.ChallengeWeekRepo()
   
    


    private lateinit var progressSms: ProgressBar

    private lateinit var rv: RecyclerView
    private lateinit var adapter: HomeExpenseDbAdapter

    private var lastSentGreenDays: Int? = null
    private var lastSentTarget: Int? = null


    private lateinit var btnImportStatement: MaterialButton
    private var unknownIndicator: View? = null

    private var weeklyProgressJob: Job? = null

    private var cashController: ManualCashEntryController? = null

    


    // ✅ Put here (top-level inside HomeFragment)
    private val pickBill =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            cashController?.onBillPicked(uri)
        }

    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnRecent: MaterialButton
    private lateinit var btnUnknown: MaterialButton

    private var listJob: Job? = null
    private var unknownCountJob: Job? = null

    private var smsObserver: SmsInboxObserver? = null

    private val PREFS = "unknown_prefs"
    private val KEY_VISITED = "unknown_visited"

    // ✅ track last known unknown count so new unknown SMS can re-show dot
    private val KEY_LAST_UNKNOWN_COUNT = "last_unknown_count"

    // ✅ SAFE prefs (fix crash: Fragment not attached)
    private fun prefsOrNull(): SharedPreferences? =
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun markUnknownVisited() {
        prefsOrNull()?.edit()?.putBoolean(KEY_VISITED, true)?.apply()
    }

    private fun resetUnknownVisited() {
        prefsOrNull()?.edit()?.putBoolean(KEY_VISITED, false)?.apply()
    }

    private fun isUnknownVisited(): Boolean =
        prefsOrNull()?.getBoolean(KEY_VISITED, false) ?: false

    private fun getLastUnknownCount(): Int =
        prefsOrNull()?.getInt(KEY_LAST_UNKNOWN_COUNT, 0) ?: 0

    private fun setLastUnknownCount(value: Int) {
        prefsOrNull()?.edit()?.putInt(KEY_LAST_UNKNOWN_COUNT, value)?.apply()
    }

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills & Utilities",
        "Entertainment", "Groceries", "Friends & Family", "Savings",
        "Rent/Hostel", "Education", "Health ,Medicine & personal care", "Others"
    )

    // ---------- name grouping helpers ----------
    private val titles = setOf(
        "mr", "mrs", "ms", "miss", "dr",
        "sir", "sri", "shri", "kum", "selvi"
    )

    private fun normalizeName(raw: String): String {
        val parts = raw.lowercase(Locale.ENGLISH)
            .replace("\u00A0", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .filter { it !in titles }
            .filter { it.length > 1 }

        return parts.joinToString(" ").trim()
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun normalizeKey(raw: String): String {
        val rawTrim = raw.trim()
        if (rawTrim.isBlank()) return ""

        val cleaned = rawTrim
            .replace("\u00A0", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        var parts = cleaned.lowercase(Locale.ENGLISH)
            .split(" ")
            .filter { it.isNotBlank() }
            .filter { it !in titles }

        if (parts.size >= 2 && parts.last().length == 1) {
            parts = parts.dropLast(1)
        }

        var key = parts.joinToString("")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()

        val hasNoSpacesOriginally = !cleaned.contains(" ")
        val looksLikeAttachedInitial =
            rawTrim.matches(Regex(""".*[A-Za-z]{4,}[A-Z]$"""))

        if (hasNoSpacesOriginally && looksLikeAttachedInitial && key.length >= 5) {
            key = key.dropLast(1)
        }

        return key
    }

    private fun preferDisplayName(a: String, b: String): String {
        val titleRegex = Regex("""\b(mr|mrs|ms|miss|dr|sir|sri|shri)\b""", RegexOption.IGNORE_CASE)
        val aHasTitle = titleRegex.containsMatchIn(a)
        val bHasTitle = titleRegex.containsMatchIn(b)

        return when {
            aHasTitle && !bHasTitle -> a
            bHasTitle && !aHasTitle -> b
            a.length >= b.length -> a
            else -> b
        }
    }
    // ------------------------------------------

    private suspend fun reCategorizeUncategorized(dao: ExpenseDao) {
        val list = dao.getUncategorizedNonUnknown()
        if (list.isEmpty()) return

        val ctx = context ?: return
        var changed = 0
        for (e in list) {
            val nm = e.name?.trim().orEmpty()
            if (nm.isBlank()) continue
            if (nm.startsWith("unknown", ignoreCase = true)) continue

            val newCat = com.example.expensereader.ml.CategoryResolver.resolve(ctx, nm)
            if (newCat.isNotBlank() && newCat != e.category) {
                val rows = dao.updateCategoryById(e.id, newCat)
                if (rows > 0) changed++
            }
        }
        Log.d("RECATEGORIZE", "done rows=${list.size} changed=$changed")
    }

    private suspend fun autoResolvePendingSingles(dao: ExpenseDao) {
        val pending = dao.getPendingUnknownWithAcc()
        if (pending.isEmpty()) return

        val ctx = context ?: return
        for (row in pending) {
            val acc = row.merchantAcc.trim()
            if (acc.isBlank()) continue

            val namesForAcc: List<String> = dao.getKnownNamesByMerchantAcc(acc)
            if (namesForAcc.isEmpty()) continue

            val merged = namesForAcc
                .filter { it.isNotBlank() }
                .groupBy { normalizeKey(it) }
                .mapValues { (_, list) -> list.reduce { a, b -> preferDisplayName(a, b) } }

            val finalNames = merged.values.distinct().sorted()

            if (finalNames.size == 1) {
                val autoName = finalNames.first()
                val resolvedCat =
                    com.example.expensereader.ml.CategoryResolver.resolve(ctx, autoName)

                val catGuess = if (resolvedCat.isNotBlank()) resolvedCat
                else dao.getLastCategoryForName(autoName) ?: "Others"

                dao.updateNameCategoryAuto(row.id, autoName, catGuess)
                dao.clearNeedsStatementImport(row.id)
            }
        }
    }

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                importSmsNow(isFirstTime = true)
                startSmsAutoImport()
            } else {
                if (context != null) {
                    Toast.makeText(requireContext(), "SMS permission denied", Toast.LENGTH_SHORT).show()
                }
                updateSavingStatusUI()
            }
        }

    private val pickPdf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                if (context != null) {
                    Toast.makeText(requireContext(), "No PDF selected", Toast.LENGTH_SHORT).show()
                }
                return@registerForActivityResult
            }

            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = context ?: return@launch
                try {
                    val txns = BankPdfParser.parse(ctx, uri)
                    StatementFromPdfSaver.applyPdfToSms(ctx, txns)

                    val dao = AppDatabase.getInstance(ctx).expenseDao()

                    dao.fixUnknownCategoryToOthers()
                    autoResolvePendingSingles(dao)
                    reCategorizeAutoRows(dao)
                    updateTodaySummary()

                    observeCurrentTab()
                    Toast.makeText(
                        ctx,
                        "Statement imported. SMS updated!",
                        Toast.LENGTH_LONG
                    ).show()

                    updateSavingStatusUI() // ✅ refresh

                } catch (e: Exception) {
                    if (context != null) {
                        Toast.makeText(
                            requireContext(),
                            "Import failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun <T : View> mustFind(id: Int, name: String): T {
            val v = view.findViewById<T>(id)
            if (v == null) {
                Log.e("HOME_BIND", "Missing view id=$name in fragment_home.xml")
                throw IllegalStateException("Missing view: $name")
            }
            return v
        }

        // ✅ BUDGET: bind only if budget card exists (prevents crash)
        val budgetRoot = view.findViewById<View>(R.id.cardBudget)
        if (budgetRoot != null) {
            budgetBinder = com.example.expensereader.ui.budget.BudgetCardBinder(
                budgetRoot,
                viewLifecycleOwner.lifecycleScope
            )
            budgetBinder?.bind()
        } else {
            Log.e("BUDGET_UI", "cardBudget not found. Check include/view_budget_card ids")
        }

        val insightCard = view.findViewById<View>(R.id.cardInsight)
        val insightBinder = InsightCardBinder(insightCard)

        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getInstance(requireContext()).expenseDao()
            val repo = InsightRepository(dao)

            val tips: List<String> = repo.generateSmartTips()

            if (tips.isNotEmpty()) {
                val model = InsightModel(
                    title = "Smart Insights",
                    message = tips.joinToString("\n"),
                    severity = InsightSeverity.WARNING,
                    score = 70
 // show all tips
                )
                insightBinder.bind(model)
            }

        }


        val weeklyCard = view.findViewById<View>(R.id.includeWeeklyChallenge)
        if (weeklyCard != null) {
            loadWeeklySingleChallenge(weeklyCard)
        }


        val btnView = mustFind<MaterialButton>(R.id.btnViewStartSaving, "btnViewStartSaving")
        btnView.setOnClickListener {

            // ✅ trigger Savings tab refresh (simple & safe)
            parentFragmentManager.setFragmentResult(
                "SAVING_STARTED_TRIGGER",
                bundleOf("ts" to System.currentTimeMillis())
            )

            // ✅ keep your existing navigation EXACTLY
            findNavController().navigate(R.id.studentExpenseFragment)
        }

        tvSavingStatus = mustFind(R.id.tvSavingStatus, "tvSavingStatus") // ✅ NEW

        tvTodaySpend = mustFind(R.id.tvTodaySpend, "tvTodaySpend")
        tvTxnCount = mustFind(R.id.tvTxnCount, "tvTxnCount")
        tvEmpty = mustFind(R.id.tvEmpty, "tvEmpty")
        tvPending = mustFind(R.id.tvPendingImports, "tvPendingImports")

        badgeImport = mustFind(R.id.badgeImport, "badgeImport")
        badgeTitle = mustFind(R.id.badgeTitle, "badgeTitle")

        progressSms = mustFind(R.id.progressSms, "progressSms")
        btnImportStatement = mustFind(R.id.btnImportStatement, "btnImportStatement")

        toggleGroup = mustFind(R.id.toggleGroup, "toggleGroup")
        btnRecent = mustFind(R.id.btnRecent, "btnRecent")
        btnUnknown = mustFind(R.id.btnUnknown, "btnUnknown")

        unknownIndicator = view.findViewById(R.id.unknownIndicator)

        rv = mustFind(R.id.rvExpenses, "rvExpenses")
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = HomeExpenseDbAdapter(
            onEdit = { expense -> showEditDialog(expense) },
            onDelete = { expense ->
                val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle("Delete Manual Expense")
                    .setMessage("Do you want to delete this manual expense?")
                    .setPositiveButton("Delete", null)
                    .setNegativeButton("Cancel", null)
                    .show()

                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

                val titleId = resources.getIdentifier("alertTitle", "id", "android")
                dialog.findViewById<TextView>(titleId)?.setTextColor(Color.BLACK)
                dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.BLACK)

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ctx = context ?: return@launch
                        AppDatabase.getInstance(ctx).expenseDao().delete(expense)
                        updateTodaySummary()
                        updateSavingStatusUI() // ✅ refresh

                        // ✅ BUDGET refresh after delete
                        budgetBinder?.refresh()
                    }
                    dialog.dismiss()
                }
            }
        )

        rv.adapter = adapter
        rv.itemAnimator = null

        
        

        cashController = ManualCashEntryController(
            root = view,
            scope = viewLifecycleOwner.lifecycleScope,
            onSaved = {
                updateTodaySummary()
                observeCurrentTab()
                updateSavingStatusUI() // ✅ refresh

                // ✅ BUDGET refresh after manual save
                budgetBinder?.refresh()
            },
            onScanBillClick = {
                startScanBill()
            }
        ).also { it.bind() }

        observePendingCount()
        observeUnknownCountButton(view)
        observeCurrentTab()
        updateTodaySummary()
        


        // ✅ BUDGET refresh after summary loaded
        budgetBinder?.refresh()

        btnImportStatement.setOnClickListener { pickPdf.launch(arrayOf("application/pdf")) }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            if (checkedId == R.id.btnUnknown) {
                markUnknownVisited()
                unknownIndicator?.visibility = View.GONE
            }
            observeCurrentTab()
        }

        ensureSmsPermissionAuto()
        updateSavingStatusUI()
        observeWeeklyChallengeProgress()


        // ✅ BUDGET final refresh (safe)
        budgetBinder?.refresh()
    }

    
    private fun loadWeeklySingleChallenge(root: View) {
        val tvTitle = root.findViewById<TextView>(R.id.tvTitle)
        val tvDesc = root.findViewById<TextView>(R.id.tvDesc)
        val tvMeta = root.findViewById<TextView>(R.id.tvMeta)

        val btnAccept = root.findViewById<MaterialButton>(R.id.btnAccept)
        val btnSkip = root.findViewById<MaterialButton>(R.id.btnSkip)

        // ✅ container for Accept+Skip row (hide whole row while tracking)
        val rowButtons = root.findViewById<View>(R.id.rowAcceptSkip)

        val progressBar =
            root.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(
                R.id.progressChallenge
            )
        val tvRight = root.findViewById<TextView>(R.id.tvProgressRight)
        val btnNext = root.findViewById<MaterialButton>(R.id.btnNext)

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch

            // ✅ CSV-based repo needs context
            val (ch, progress) = weekRepo.getThisWeekChallenge(ctx)
            if (ch == null) return@launch

            tvTitle.text = "${ch.emoji} ${ch.title}"
            tvDesc.text = ch.description

            val computedTarget = when (progress.currentOrder) {
                1 -> 3
                2 -> 5
                3 -> 7
                else -> 1
            }
            val finalTarget = if (progress.target > 0) progress.target else computedTarget
            val prog = progress.progress.coerceAtLeast(0)
            val status = progress.status.uppercase()

            val green = ContextCompat.getColor(ctx, android.R.color.holo_green_dark)
            val blue = ContextCompat.getColor(ctx, R.color.cobalt_blue)
            val gray = ContextCompat.getColor(ctx, R.color.text_muted)

            fun showProgress(show: Boolean) {
                progressBar.visibility = if (show) View.VISIBLE else View.GONE
                tvRight.visibility = if (show) View.VISIBLE else View.GONE
            }

            fun showButtons(show: Boolean) {
                rowButtons?.visibility = if (show) View.VISIBLE else View.GONE
            }

            fun showNext(show: Boolean) {
                btnNext.visibility = if (show) View.VISIBLE else View.GONE
            }

            when (status) {
                "ACTIVE" -> {
                    tvMeta.text = "Reward: +${ch.rewardPoints} Points • Time Left: 6 Days"

                    showProgress(false)
                    showButtons(true)
                    showNext(false)

                    btnAccept.text = "✔ Accept Challenge"
                    btnAccept.isEnabled = true
                    btnSkip.isEnabled = true
                }

                "ACCEPTED" -> {
                    tvMeta.text = "Reward: +${ch.rewardPoints} Points • Progress: $prog/$finalTarget"

                    showProgress(true)
                    progressBar.max = finalTarget
                    progressBar.progress = prog.coerceAtMost(finalTarget)
                    progressBar.setIndicatorColor(blue)
                    progressBar.trackColor = gray
                    tvRight.text = "$prog/$finalTarget"

                    // ✅ while tracking: NO buttons
                    showButtons(false)
                    showNext(false)
                }

                "COMPLETED" -> {
                    tvMeta.text = "Reward: +${ch.rewardPoints} Points • ✅ Completed"

                    showProgress(true)
                    progressBar.max = finalTarget
                    progressBar.progress = finalTarget
                    progressBar.setIndicatorColor(green)
                    progressBar.trackColor = green
                    tvRight.text = "Completed ✅"

                    // ✅ completed: show only NEXT
                    showButtons(false)
                    showNext(true)
                    btnNext.text = "Next Challenge ➜"
                }

                else -> {
                    tvMeta.text = "Reward: +${ch.rewardPoints} Points"

                    showProgress(false)
                    showButtons(true)
                    showNext(false)

                    btnAccept.text = "✔ Accept Challenge"
                    btnAccept.isEnabled = true
                    btnSkip.isEnabled = true
                }
            }

            // ✅ Clicks
            btnAccept.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val c = context ?: return@launch
                    weekRepo.acceptThisWeek(c, target = finalTarget, title = ch.title)
                    loadWeeklySingleChallenge(root)
                }
            }

            btnSkip.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val c = context ?: return@launch
                    weekRepo.skipAndAdvanceThisWeek(c) // ✅ loops 1..50 forever
                    loadWeeklySingleChallenge(root)
                }
            }

            btnNext.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val c = context ?: return@launch
                    weekRepo.skipAndAdvanceThisWeek(c) // ✅ loops 1..50 forever
                    loadWeeklySingleChallenge(root)
                }
            }
        }
    }


    private fun hideKeyboard(view: View?) {
        if (view == null) return
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }



    override fun onStart() {
        super.onStart()
        startSmsAutoImport()
        updateSavingStatusUI()
        budgetBinder?.refresh()
    }

    override fun onStop() {
        super.onStop()
        stopSmsAutoImport()
    }

    private fun ensureSmsPermissionAuto() {
        val ctx = context ?: return
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            importSmsNow(isFirstTime = false)
            startSmsAutoImport()
        } else {
            requestSmsPermission.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun startSmsAutoImport() {
        val ctx = context ?: return
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        if (smsObserver == null) {
            smsObserver = SmsInboxObserver(ctx, viewLifecycleOwner)
        }
        smsObserver?.register()
    }

    

    
    


    private fun stopSmsAutoImport() {
        smsObserver?.unregister()
    }

    private fun observeCurrentTab() {
        listJob?.cancel()

        val ctx = context ?: return
        val dao = AppDatabase.getInstance(ctx).expenseDao()

        listJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val showUnknownTab = toggleGroup.checkedButtonId == R.id.btnUnknown
                val flow = if (showUnknownTab) {
                    dao.getUnknownSmsFlow()
                } else {
                    val startOfDay = startOfTodayMillis()
                    dao.getRecentSmsTodayFlow(startOfDay)
                }

                flow.collectLatest { list -> renderExpenses(list) }
            }
        }
    }

    private fun observePendingCount() {
        val ctx = context ?: return
        val dao = AppDatabase.getInstance(ctx).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getPendingImportCountFlow().collectLatest { count ->
                    if (count > 0) {
                        tvPending.visibility = View.VISIBLE
                        tvPending.text = "Pending imports (statement needed): $count"
                    } else {
                        tvPending.visibility = View.GONE
                    }

                    if (count > 0) {
                        badgeImport.visibility = View.VISIBLE
                        badgeImport.text = count.toString()

                        badgeTitle.visibility = View.VISIBLE
                        badgeTitle.text = count.toString()
                    } else {
                        badgeImport.visibility = View.GONE
                        badgeTitle.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun observeUnknownCountButton(root: View) {
        unknownCountJob?.cancel()

        val ctx = context ?: return
        val dao = AppDatabase.getInstance(ctx).expenseDao()
        val indicator = root.findViewById<View>(R.id.unknownIndicator)

        unknownCountJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getPendingImportCountFlow().collectLatest { count ->

                    btnUnknown.text = if (count > 0) "Unknown ($count)" else "Unknown"

                    val last = getLastUnknownCount()
                    val newUnknownArrived = count > last
                    if (newUnknownArrived) resetUnknownVisited()

                    val showDot = (count > 0) && !isUnknownVisited()
                    indicator.visibility = if (showDot) View.VISIBLE else View.GONE

                    setLastUnknownCount(count)
                }
            }
        }
    }

    private fun importSmsNow(isFirstTime: Boolean) {
        progressSms.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: run {
                progressSms.visibility = View.GONE
                return@launch
            }

            try {
                SmsReader.importNew(ctx)

                val dao = AppDatabase.getInstance(ctx).expenseDao()

                val unknownCount = dao.getPendingImportCount()
                val last = getLastUnknownCount()
                if (unknownCount > last) {
                    resetUnknownVisited()
                }
                setLastUnknownCount(unknownCount)

                dao.fixUnknownCategoryToOthers()
                autoResolvePendingSingles(dao)
                reCategorizeAutoRows(dao)
                updateTodaySummary()
                budgetBinder?.refresh()

                if (isFirstTime) {
                    Toast.makeText(
                        ctx,
                        "SMS auto-import enabled!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                observeCurrentTab()

                // ✅ refresh saving status after new SMS import
                updateSavingStatusUI()

            } catch (e: Exception) {
                if (context != null) {
                    Toast.makeText(
                        requireContext(),
                        "SMS import failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                progressSms.visibility = View.GONE
            }
        }
    }

    private fun renderExpenses(list: List<Expense>) {
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateTodaySummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            try {
                val dao = AppDatabase.getInstance(ctx).expenseDao()
                val startOfDay = startOfTodayMillis()

                // ✅ Total today (all SMS + unknown + known)
                val todayTotalAll = dao.getTodayTotalAll(startOfDay)
                val todayCountAll = dao.getTodayTxnCountAll(startOfDay)

                // ✅ Unknown today (same rule as Unknown tab)
                val unknown = dao.getTodayUnknownSummary(startOfDay)

                // ✅ If you want to show ALL (recommended)
                tvTodaySpend.text = "₹ ${todayTotalAll.toInt()}"
                tvTxnCount.text = todayCountAll.toString()

                // ✅ Debug (remove later)
                Log.d(
                    "HOME_SUMMARY",
                    "allTotal=$todayTotalAll allCount=$todayCountAll unknownTotal=${unknown.total} unknownCount=${unknown.cnt}"
                )

            } catch (e: Exception) {
                Log.e("TODAY_SUMMARY", "Failed to update today summary", e)
            }
        }
    }

    private fun updateSavingTitle(view: View) {
        val title = view.findViewById<TextView>(R.id.tvHomeTitle)
        title?.text = if (SmartSavingPrefs.isStarted(requireContext())) {
            "Daily Saving"
        } else {
            "Start Saving"
        }
    }

    


    // ✅ Start/Found/Not found UI
    private fun updateSavingStatusUI() {
        val root = view ?: return
        val tv = root.findViewById<TextView>(R.id.tvSavingStatus) ?: return
        val btnView = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewStartSaving)
        val tracker = root.findViewById<View>(R.id.weeklyTrackerContainer)

        // ✅ day circles
        val dayViews: List<ImageView> = listOf(
            root.findViewById(R.id.day0),
            root.findViewById(R.id.day1),
            root.findViewById(R.id.day2),
            root.findViewById(R.id.day3),
            root.findViewById(R.id.day4),
            root.findViewById(R.id.day5),
            root.findViewById(R.id.day6)
        )

        // ✅ flags above circles
        val flagViews: List<ImageView> = listOf(
            root.findViewById(R.id.day0Flag),
            root.findViewById(R.id.day1Flag),
            root.findViewById(R.id.day2Flag),
            root.findViewById(R.id.day3Flag),
            root.findViewById(R.id.day4Flag),
            root.findViewById(R.id.day5Flag),
            root.findViewById(R.id.day6Flag)
        )

        // ✅ connecting lines
        val lineViews: List<View> = listOf(
            root.findViewById(R.id.line0),
            root.findViewById(R.id.line1),
            root.findViewById(R.id.line2),
            root.findViewById(R.id.line3),
            root.findViewById(R.id.line4),
            root.findViewById(R.id.line5)
        )

        // ✅ IMPORTANT: always update title based on started
        updateSavingTitle(root)

        // 1) Not started
        if (!SmartSavingPrefs.isStarted(requireContext())) {
            btnView?.visibility = View.VISIBLE
            tracker?.visibility = View.GONE
            tv.text = "You have not started saving yet."

            // safety: hide flags
            for (f in flagViews) f.visibility = View.GONE
            return
        }

        // 2) Started
        btnView?.visibility = View.GONE
        tracker?.visibility = View.VISIBLE

        // 3) Load weekly saving status from DB
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(requireContext()).expenseDao()

                val (windowStart, windowEnd) = thisWeekRange()

                val tzOffset = java.util.TimeZone.getDefault().getOffset(windowStart).toLong()
                val rows = dao.getSavingCountsByDay(windowStart, windowEnd, tzOffset)
                val doneDays: Set<Long> = rows.map { it.dayStart }.toSet()

                val todayIndex =
                ((System.currentTimeMillis() - windowStart) / 86400000L)
                    .toInt().coerceIn(0, 6)
// ✅ in a rolling 7-day window, today is always last circle

                val blue = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)
                val gray = ContextCompat.getColor(requireContext(), R.color.text_muted)

                // reset lines to gray
                for (ln in lineViews) ln.setBackgroundColor(gray)

                // reset flags hidden
                for (f in flagViews) f.visibility = View.GONE

                // ✅ set circle + flag
                for (i in 0..6) {
                    val dayStart = windowStart + (i * 86400000L)
                    val isDone = doneDays.contains(dayStart)

                     val iconRes = when {
                        isDone -> R.drawable.ic_day_done
                        i < todayIndex -> R.drawable.ic_day_missed          // ✅ RED for not done
                        i == todayIndex -> R.drawable.ic_day_today_pending
                        else -> R.drawable.ic_day_future
                    }
                    dayViews[i].setImageResource(iconRes)
                    flagViews[i].visibility = if (isDone) View.VISIBLE else View.GONE
                }

                // trending line: make segment blue if left day is done
                for (i in 0..5) {
                    val leftDayStart = windowStart + (i * 86400000L)
                    if (doneDays.contains(leftDayStart)) lineViews[i].setBackgroundColor(blue)
                }

            tv.text = "This week: ${doneDays.size} / 7 days"


            } catch (e: Exception) {
                tv.text = "Unable to load weekly saving status"
                Log.e("SAVING_UI", "updateSavingStatusUI failed", e)
            }
        }
    }

    // ✅ EDIT DIALOG (same as your code)
    private fun showEditDialog(expense: Expense) {

        val cobalt = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)

        val nameEt = AutoCompleteTextView(requireContext()).apply {
            setText(expense.name, false)
            hint = "Name"
            threshold = 1

            setBackgroundResource(R.drawable.bg_edit_white)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)

            setDropDownBackgroundDrawable(ColorDrawable(Color.WHITE))
        }

        val spinner = Spinner(requireContext()).apply {
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_spinner_blue_outline
            )

            adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                categories
            ).also {
                it.setDropDownViewResource(R.layout.item_spinner_black_dropdown)
            }
        }

        val selectedIndex =
            categories.indexOf(expense.category).takeIf { it >= 0 } ?: categories.indexOf("Others")
        spinner.setSelection(selectedIndex)

        val suggestedBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
        }

        val suggestedTitle = TextView(requireContext()).apply {
            text = "Choose name for THIS SMS (same Acc No):"
            textSize = 13f
            setPadding(0, 12, 0, 8)
            setTextColor(cobalt)
            setBackgroundColor(Color.WHITE)
        }
        suggestedBox.addView(suggestedTitle)

        val suggestedList = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        suggestedBox.addView(suggestedList)

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            setBackgroundColor(Color.WHITE)

            addView(nameEt)
            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 14
                )
                setBackgroundColor(Color.WHITE)
            })
            addView(spinner)
            addView(suggestedBox)
        }

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                container,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        scrollView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.75f).toInt()
        )

        val isUnknownSms = expense.name.trim().startsWith("unknown", ignoreCase = true)

        val dialog = AlertDialog.Builder(
            requireContext(),
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setTitle("Edit Expense (Only this SMS)")
            .setView(scrollView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.findViewById<TextView>(
            requireContext().resources.getIdentifier("alertTitle", "id", "android")
        )?.setTextColor(cobalt)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cobalt)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cobalt)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = context ?: return@launch
                val dao = AppDatabase.getInstance(ctx).expenseDao()

                val rawNames = dao.getAllKnownNamesForSuggest()
                val grouped = mutableMapOf<String, String>()
                for (n in rawNames) {
                    val key = normalizeKey(n)
                    if (key.isBlank()) continue
                    val existing = grouped[key]
                    grouped[key] = if (existing == null) n else preferDisplayName(existing, n)
                }
                val suggestionListGlobal = grouped.values.distinct().sorted()

                nameEt.setAdapter(
                    ArrayAdapter(
                        ctx,
                        R.layout.item_dropdown_black,
                        suggestionListGlobal
                    )
                )

                if (isUnknownSms) {
                    val acc = expense.merchantAcc?.trim()
                    if (!acc.isNullOrBlank()) {

                        val namesForAcc: List<String> = dao.getKnownNamesByMerchantAcc(acc)

                        if (namesForAcc.isNotEmpty()) {

                            val merged = namesForAcc
                                .filter { it.isNotBlank() }
                                .groupBy { normalizeKey(it) }
                                .mapValues { (_, list) ->
                                    list.reduce { a, b -> preferDisplayName(a, b) }
                                }

                            val finalNames = merged.values.distinct().sorted()
                            suggestedList.removeAllViews()

                            if (finalNames.size == 1) {
                                val autoName = finalNames.first()
                                val resolvedCat =
                                    com.example.expensereader.ml.CategoryResolver.resolve(ctx, autoName)
                                val catGuess =
                                    if (resolvedCat.isNotBlank()) resolvedCat
                                    else (dao.getLastCategoryForName(autoName) ?: "Others")

                                dao.updateNameCategory(expense.id, autoName, catGuess)
                                dao.clearNeedsStatementImport(expense.id)

                                hideKeyboard(nameEt)
                                nameEt.clearFocus()


                                dialog.dismiss()
                                toggleGroup.check(R.id.btnRecent)
                                observeCurrentTab()

                                Toast.makeText(ctx, "Auto updated: $autoName", Toast.LENGTH_SHORT).show()
                                updateSavingStatusUI()
                                return@launch
                            }

                            suggestedBox.visibility = View.VISIBLE

                            finalNames.forEach { nm ->
                                val btn = MaterialButton(
                                    requireContext(),
                                    null,
                                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                                ).apply {
                                    text = nm
                                    isAllCaps = false

                                    setBackgroundColor(Color.WHITE)
                                    setTextColor(Color.BLACK)
                                    strokeColor = ContextCompat.getColorStateList(
                                        requireContext(),
                                        R.color.cobalt_blue
                                    )

                                    setOnClickListener {
                                        nameEt.setText(nm, false)
                                        nameEt.setSelection(nameEt.text?.length ?: 0)
                                        nameEt.dismissDropDown()

                                        viewLifecycleOwner.lifecycleScope.launch {
                                            try {
                                                val cg = dao.getLastCategoryForName(nm) ?: "Others"
                                                val idx = categories.indexOf(cg)
                                                    .takeIf { it >= 0 } ?: categories.indexOf("Others")
                                                spinner.setSelection(idx)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                                suggestedList.addView(btn)
                            }
                        } else {
                            suggestedBox.visibility = View.GONE
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("EDIT_SUGGEST", "Failed loading suggestions", e)
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {

            val newName = nameEt.text.toString().trim()
            val newCat = spinner.selectedItem?.toString()?.trim().orEmpty()

            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val ctx = context ?: return@launch
                    val dao = AppDatabase.getInstance(ctx).expenseDao()

                    dao.updateNameCategory(expense.id, newName, newCat)
                    dao.clearNeedsStatementImport(expense.id)
                    updateTodaySummary()

                    Toast.makeText(ctx, "Updated this SMS only", Toast.LENGTH_SHORT).show()

                    toggleGroup.check(R.id.btnRecent)
                    observeCurrentTab()
                    dialog.dismiss()
                    updateSavingStatusUI()

                } catch (e: Exception) {
                    if (context != null) {
                        Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private suspend fun reCategorizeAutoRows(dao: ExpenseDao) {
        val list = dao.getAutoRowsNeedingCategory()
        if (list.isEmpty()) return

        val ctx = context ?: return
        var changed = 0
        for (row in list) {
            val nm = row.name?.trim().orEmpty()
            if (nm.isBlank()) continue

            val newCat = com.example.expensereader.ml.CategoryResolver.resolve(ctx, nm)
            if (newCat.isNotBlank() && newCat != (row.category ?: "")) {
                val rows = dao.updateCategoryById(row.id, newCat)
                if (rows > 0) changed++
            }
        }

        Log.d("RECATEG_AUTO", "rows=${list.size} changed=$changed")
    }

    private var cameraImageUri: Uri? = null

    private val pickFromGallery =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                cashController?.onBillPicked(uri)
            } else {
                Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
            }
        }

    private val takeBillPhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                cashController?.onBillPicked(cameraImageUri)
            } else {
                openGalleryFallback()
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCameraDirect()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    showCameraSettingsDialog()
                } else {
                    Toast.makeText(requireContext(), "Camera denied. Opening gallery.", Toast.LENGTH_SHORT).show()
                    openGalleryFallback()
                }
            }
        }

    private fun createCameraImageUri(): Uri {
        val file = java.io.File(requireContext().cacheDir, "bill_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }

    private fun openCameraDirect() {
        cameraImageUri = createCameraImageUri()
        takeBillPhoto.launch(cameraImageUri)
    }

    private fun openGalleryFallback() {
        pickFromGallery.launch(arrayOf("image/*"))
    }

    private fun startScanBill() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            openCameraDirect()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showCameraSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Camera Permission Needed")
            .setMessage("To scan bills, allow Camera permission in Settings. Or choose from Gallery.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
            .setNegativeButton("Use Gallery") { _, _ ->
                openGalleryFallback()
            }
            .show()
    }

    private fun startOfWeekMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun observeWeeklyChallengeProgress() {
        weeklyProgressJob?.cancel()

        weeklyProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ✅ We will observe based on ACCEPTED start day (7 days from accept)
                var innerJob: Job? = null

                // loop-style: keep checking current challenge state
                while (true) {
                    val c = context ?: break

                    try {
                        val (_, progress) = weekRepo.getThisWeekChallenge(c)

                        val computedTarget = when (progress.currentOrder) {
                            1 -> 3
                            2 -> 5
                            3 -> 7
                            else -> 1
                        }
                        val finalTarget = if (progress.target > 0) progress.target else computedTarget

                        val shouldTrack = progress.status.equals("ACCEPTED", ignoreCase = true)

                        // ✅ Stop previous collector if status changed
                        if (!shouldTrack) {
                            innerJob?.cancel()
                            innerJob = null
                            lastSentGreenDays = null
                            lastSentTarget = null

                            // still refresh card UI
                            refreshWeeklyChallengeCard()
                        } else {
                            // ✅ Start tracking window from ACCEPT day
                            // NOTE: progress.startDayMillis must be saved by repo when Accept is pressed
                            val (windowStart, windowEnd) = accepted7DayRange(progress.startDayMillis)

                            if (innerJob == null) {
                                val dao = AppDatabase.getInstance(c).expenseDao()

                                innerJob = viewLifecycleOwner.lifecycleScope.launch {
                                    dao.observeDailyTotalsBetween(windowStart, windowEnd)
                                        .collectLatest { totals ->

                                            val ctxNow = context ?: return@collectLatest

                                            val dailyLimit = BudgetManager.getDailyLimit(ctxNow).toDouble()
                                            val greenDays = totals.count { it.total <= dailyLimit }

                                            // ✅ update progress only when ACCEPTED
                                            if (lastSentGreenDays != greenDays || lastSentTarget != finalTarget) {
                                                weekRepo.updateProgress(
                                                    ctxNow,
                                                    progress = greenDays,
                                                    target = finalTarget
                                                )
                                                lastSentGreenDays = greenDays
                                                lastSentTarget = finalTarget
                                            }

                                            refreshWeeklyChallengeCard()
                                        }
                                }
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("WEEKLY_CHALLENGE", "observeWeeklyChallengeProgress loop failed", e)
                    }

                    // ✅ small delay so we don't busy-loop
                    kotlinx.coroutines.delay(800L)
                }
            }
        }
    }

    private fun thisWeekRange(): Pair<Long, Long> {
        val weekStart = startOfWeekMillis()
        val weekEnd = weekStart + (7L * 86400000L) - 1L   // end of Sunday
        return Pair(weekStart, weekEnd)
    }


    private fun refreshWeeklyChallengeCard() {
        val rootView = view ?: return
        val weeklyCard = rootView.findViewById<View>(R.id.includeWeeklyChallenge) ?: return
        loadWeeklySingleChallenge(weeklyCard)
    }

    private fun startOfDayMillis(t: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = t
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }


    private fun last7DaysRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val todayStart = startOfDayMillis(now)
        val windowStart = todayStart - (6L * 86400000L)      // ✅ today + previous 6 days
        val windowEnd = todayStart + (86400000L - 1L)        // ✅ end of today
        return Pair(windowStart, windowEnd)
    }

    private fun accepted7DayRange(startDayMillis: Long): Pair<Long, Long> {
        val start = if (startDayMillis > 0L) startDayMillis else startOfDayMillis(System.currentTimeMillis())
        val end = start + (7L * 86400000L) - 1L
        return Pair(start, end)
    }


    




    
}
