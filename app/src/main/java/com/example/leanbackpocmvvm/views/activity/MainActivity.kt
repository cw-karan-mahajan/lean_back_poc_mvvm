package com.example.leanbackpocmvvm.views.activity

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.views.fragment.MainFragment
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.application.GlideApp
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var isActivityDestroyed = false
    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager

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

        // Clear all fragments
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is MainFragment) {
            fragment.onDestroy()
        }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        //if (exoPlayerManager.isPlayerReleased()) {
            exoPlayerManager.reinitializePlayer()
       // }
    }

    override fun onPause() {
        super.onPause()
        //if (!exoPlayerManager.isPlayerReleased()) {
            exoPlayerManager.releasePlayer()
        //}
    }
}