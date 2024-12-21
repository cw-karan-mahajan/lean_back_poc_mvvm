package com.example.leanbackpocmvvm.utils;

import timber.log.Timber;

public class NoLoggingTree extends Timber.Tree {
    @Override
    protected void log(final int priority, final String tag, final String message, final Throwable throwable) {}
}