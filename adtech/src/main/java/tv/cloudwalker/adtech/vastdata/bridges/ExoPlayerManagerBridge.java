package tv.cloudwalker.adtech.vastdata.bridges;

import androidx.media3.ui.PlayerView;
import androidx.annotation.NonNull;
import tv.cloudwalker.adtech.vastdata.parser.VastParser;

public interface ExoPlayerManagerBridge {
    interface ProgressCallback {
        void onProgressUpdate(long currentPosition, long duration, int adNumber, int totalAds);
    }

    void prepareVideo(
            @NonNull String videoUrl,
            @NonNull PlayerView playerView,
            @NonNull OnReadyCallback onReady,
            @NonNull OnEndedCallback onEnded,
            boolean isPartOfSequence,
            @NonNull VastParser.VastAd vastAd,
            int adNumber,
            int totalAds
    );

    void setProgressCallback(ProgressCallback callback);
    void releasePlayer();
    void onLifecycleDestroy();

    interface OnReadyCallback {
        void onReady(boolean isReady);
    }

    interface OnEndedCallback {
        void onEnded();
    }
}