package com.ghostchu.crowdin.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ForkJoinPoolUtilTest {

    @Test
    void createExecutorService() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ForkJoinPoolUtil.createExecutorService(0));
    }
}