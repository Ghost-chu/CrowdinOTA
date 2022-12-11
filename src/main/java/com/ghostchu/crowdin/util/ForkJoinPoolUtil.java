package com.ghostchu.crowdin.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ForkJoinPoolUtil {
    public static ExecutorService createExecutorService(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("The thread count must be greater than 0");
        }
        return new ThreadPoolExecutor(0, threadCount, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    }
}
