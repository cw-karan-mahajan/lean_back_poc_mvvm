package com.example.leanbackpocmvvm.vastdata.bridges;

import androidx.annotation.NonNull;

public interface TrackingBridge {
    interface TrackingCallback {
        void onStartTracking(@NonNull String vastId);
        void onFirstQuartile(@NonNull String vastId);
        void onMidpoint(@NonNull String vastId);
        void onThirdQuartile(@NonNull String vastId);
        void onComplete(@NonNull String vastId);
        void onError(@NonNull String vastId, @NonNull String message);
    }

    void trackEvent(@NonNull String vastId, @NonNull String eventType);
    void setTrackingCallback(@NonNull TrackingCallback callback);
    void cancelTracking(@NonNull String vastId);
    void cancelAllTracking();
}
