package com.example.leanbackpocmvvm.views.activity

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.views.fragment.MainFragment
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.application.GlideApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var isActivityDestroyed = false

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment())
                .commit()
        }
    }

    fun safelyUseGlide(action: () -> Unit) {
        if (!isActivityDestroyed && !isFinishing) {
            action()
        }
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        Glide.with(this).pauseRequests()
        super.onDestroy()
    }
}