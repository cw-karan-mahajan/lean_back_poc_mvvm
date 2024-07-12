package com.example.leanbackpocmvvm.views.presenter

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.leanback.widget.TitleViewAdapter
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.databinding.CustomTitleViewBinding

class CustomTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), TitleViewAdapter.Provider {

    private val binding: CustomTitleViewBinding = CustomTitleViewBinding.inflate(LayoutInflater.from(context), this)

    private val titleViewAdapter = object : TitleViewAdapter() {
        override fun getSearchAffordanceView() = null
    }

    private val logoMap = mapOf(
        R.id.search_logo to binding.searchLogo,
        R.id.settinglogo to binding.settinglogo,
        R.id.profile_app to binding.profileApp,
        R.id.wifilogo to binding.wifilogo,
        R.id.inputlogo to binding.inputlogo,
        R.id.allappslogo to binding.allappslogo
    )

    init {
        setupClickListeners()
        setupKeyListeners()
    }

    private fun setupClickListeners() {
        logoMap.forEach { (id, view) ->
            view.setOnClickListener {
                when (id) {
                    R.id.settinglogo -> launchSettings(Settings.ACTION_SETTINGS)
                    R.id.wifilogo -> launchSettings(Settings.ACTION_WIFI_SETTINGS)
                    else -> showToast("You Clicked ${resources.getResourceEntryName(id)}")
                }
            }
        }
    }

    private fun setupKeyListeners() {
        val keyOrder = listOf(R.id.profile_app, R.id.search_logo, R.id.settinglogo, R.id.wifilogo, R.id.inputlogo, R.id.allappslogo)
        keyOrder.forEachIndexed { index, id ->
            logoMap[id]?.setOnKeyListener { _, keyCode, event ->
                when {
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN -> {
                        logoMap[keyOrder[(index + 1) % keyOrder.size]]?.requestFocus()
                        true
                    }
                    keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN -> {
                        logoMap[id]?.requestFocus()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchSettings(action: String) {
        showToast("You Clicked ${action.substringAfterLast('.')}")
        context.startActivity(Intent(action))
    }

    override fun getTitleViewAdapter(): TitleViewAdapter = titleViewAdapter
}