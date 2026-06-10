package com.example.expensereader.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.expensereader.R
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout

class AuthFragment : Fragment(R.layout.fragment_auth) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tab = view.findViewById<TabLayout>(R.id.tabAuth)
        val pager = view.findViewById<ViewPager2>(R.id.pagerAuth)

        pager.adapter = AuthPagerAdapter(this)

        TabLayoutMediator(tab, pager) { t, pos ->
            t.text = if (pos == 0) "Login" else "Register"
        }.attach()
    }
}
