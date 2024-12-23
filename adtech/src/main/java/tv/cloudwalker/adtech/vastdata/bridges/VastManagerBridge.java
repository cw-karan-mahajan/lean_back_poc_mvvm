package tv.cloudwalker.adtech.vastdata.bridges;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;

public interface VastManagerBridge {
    interface VastEvents {
        void onAdReady(boolean isReady);
        void onAdProgress(long currentPosition, long duration, int adNumber, int totalAds);
        void onQuartileReached(int quartile);
        void onAdComplete();
        void onAdError(String error);
        void onImpressionTracked(String vastId);
        void onClickTracked(String vastId);
    }

    void initialize(@NonNull Context context);

    void prepareAd(@NonNull String vastUrl,
                   @NonNull String tileId,
                   @NonNull PlayerView playerView,
                   @NonNull VastEvents eventListener);

    void stopAd();
    void pauseAd();
    void resumeAd();
    void releaseAd();

    boolean isPlaying();
    void setVolume(float volume);
    float getVolume();
    void cleanup();
}