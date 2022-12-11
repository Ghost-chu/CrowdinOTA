package com.ghostchu.crowdin.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForkJoinPoolUtil {
    @NotNull
    public static ExecutorService createExecutorService(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("The thread count must be greater than 0");
        }
        return Executors.newFixedThreadPool(threadCount);
    }
}
