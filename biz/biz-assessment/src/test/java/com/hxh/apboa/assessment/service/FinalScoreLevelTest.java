package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinalScoreLevelTest {
    @Test
    void maps100PointScoresToDefaultLevels() {
        assertEquals("EXCELLENT", FinalScoreLevel.of(90));
        assertEquals("GOOD", FinalScoreLevel.of(75));
        assertEquals("PASS", FinalScoreLevel.of(60));
        assertEquals("FAIL", FinalScoreLevel.of(59));
    }
}
