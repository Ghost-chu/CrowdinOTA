package com.ghostchu.crowdin.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DigestUtilTest {

    @Test
    void sha1() {
        Assertions.assertEquals(DigestUtil.sha1("A smart fox jumps over a lazy dog."), "1d7bbfd270bc2b408584b14804ecd63bf7a0b511");
    }
}