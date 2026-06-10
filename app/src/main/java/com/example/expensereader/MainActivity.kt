package com.example.expensereader

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var drawerItems: List<TextView> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val toolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navHostView = findViewById<View>(R.id.navHost)

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController

        // ✅ Start destination (TEMP): Always go Home
        val inflater = navController.navInflater
        val graph = inflater.inflate(R.navigation.nav_graph)

        graph.setStartDestination(R.id.homeFragment)

        navController.graph = graph

        // ✅ Connect bottom nav with nav controller (ONLY 5 tabs)
        bottomNav.setupWithNavController(navController)

        // ✅ Drawer views list (for highlight)
        val tvHome = findViewById<TextView>(R.id.menuHome)
        val tvCategory = findViewById<TextView>(R.id.menuCategory)
        val tvAnalysis = findViewById<TextView>(R.id.menuAnalysis)
        val tvSavings = findViewById<TextView>(R.id.menuSavings)
        val tvProfile = findViewById<TextView>(R.id.menuProfile)

        drawerItems = listOfNotNull(tvHome, tvCategory, tvAnalysis, tvSavings, tvProfile)
        tvHome?.let { highlightDrawerItem(it) }

        // ✅ Close button (drawer)
        findViewById<ImageView>(R.id.btnCloseDrawer)?.setOnClickListener {
            drawer.closeDrawer(GravityCompat.START)
        }

        // ✅ switch bottom tab by index (0..4)
        fun goTab(index: Int, itemView: TextView?) {
            drawer.closeDrawer(GravityCompat.START)
            itemView?.let { highlightDrawerItem(it) }

            val item = bottomNav.menu.getItem(index)
            bottomNav.selectedItemId = item.itemId
        }

        tvHome?.setOnClickListener { goTab(0, tvHome) }
        tvCategory?.setOnClickListener { goTab(1, tvCategory) }
        tvAnalysis?.setOnClickListener { goTab(2, tvAnalysis) }
        tvSavings?.setOnClickListener { goTab(3, tvSavings) }
        tvProfile?.setOnClickListener { goTab(4, tvProfile) }

        // ✅ Logout
        findViewById<MaterialButton>(R.id.btnLogout)?.setOnClickListener {
            drawer.closeDrawer(GravityCompat.START)
            doLogout()
        }

        // ✅ Only these screens should show top icons + hamburger menu
        val mainTabs = setOf(
            R.id.homeFragment,
            R.id.categoryFragment,
            R.id.analysisFragment,
            R.id.savingsFragment,
            R.id.profileFragment
        )

        // ✅ Toolbar menu click (Help + Profile)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_helper -> {
                    true
                }
                R.id.action_profile -> {
                    // go to Profile tab
                    val navItem = bottomNav.menu.getItem(4)
                    bottomNav.selectedItemId = navItem.itemId
                    tvProfile?.let { highlightDrawerItem(it) }
                    true
                }
                else -> false
            }
        }

        // ✅ Update toolbar + bottom nav visibility + icons visibility
        navController.addOnDestinationChangedListener { _, destination, _ ->
            toolbar.title = destination.label?.toString() ?: "EAS"

            val isMainTab = mainTabs.contains(destination.id)

            // ✅ Since start is always home, auth screen is not used now
            val isAuth = destination.id == R.id.authFragment

            // ✅ Hide toolbar+bottom nav in Auth screen (still kept for later)
            toolbar.visibility = if (isAuth) View.GONE else View.VISIBLE

            // 1) Bottom nav only in 5 tabs (and not in auth)
            bottomNav.visibility = if (isMainTab && !isAuth) View.VISIBLE else View.GONE

            // ✅ remove bottom empty space when bottom nav is hidden
            val bottomPad = if (isMainTab && !isAuth) dpToPx(72) else 0
            navHostView.setPadding(0, 0, 0, bottomPad)

            // 2) Top right icons rule:
            updateToolbarMenu(toolbar.menu, destination.id)

            // 3) Left icon: Menu in 5 tabs, Back in inner pages
            if (isMainTab && !isAuth) {
                toolbar.setNavigationIcon(R.drawable.ic_menu_24)
                toolbar.setNavigationOnClickListener {
                    drawer.openDrawer(GravityCompat.START)
                }
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            } else {
                if (!isAuth) {
                    toolbar.setNavigationIcon(R.drawable.ic_back_white_24)
                    toolbar.setNavigationOnClickListener {
                        navController.navigateUp()
                    }
                }

                drawer.closeDrawer(GravityCompat.START)
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }

            // 4) Highlight drawer only for main tabs
            when (destination.id) {
                R.id.homeFragment -> tvHome?.let { highlightDrawerItem(it) }
                R.id.categoryFragment -> tvCategory?.let { highlightDrawerItem(it) }
                R.id.analysisFragment -> tvAnalysis?.let { highlightDrawerItem(it) }
                R.id.savingsFragment -> tvSavings?.let { highlightDrawerItem(it) }
                R.id.profileFragment -> tvProfile?.let { highlightDrawerItem(it) }
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun updateToolbarMenu(menu: Menu, destinationId: Int) {
        val helpItem = menu.findItem(R.id.action_helper)
        val profileItem = menu.findItem(R.id.action_profile)

        when (destinationId) {
            R.id.profileFragment -> {
                helpItem?.isVisible = true
                profileItem?.isVisible = false
            }

            R.id.homeFragment,
            R.id.categoryFragment,
            R.id.analysisFragment,
            R.id.savingsFragment -> {
                helpItem?.isVisible = true
                profileItem?.isVisible = true
            }

            else -> {
                helpItem?.isVisible = false
                profileItem?.isVisible = false
            }
        }
    }

    private fun highlightDrawerItem(selected: TextView) {
        drawerItems.forEach { it.background = null }
        selected.setBackgroundResource(R.drawable.bg_drawer_item_selected)
    }

    private fun doLogout() {
        // ✅ for now (same as your logic)
        finishAffinity()
    }
}
