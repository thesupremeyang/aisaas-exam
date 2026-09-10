package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamScoreConverterTest {
    @Test
    void convertsRawRubricScoreToConfiguredQuestionScore() {
        assertEquals(18, ExamScoreConverter.toQuestionScore(72, 100, 25));
        assertEquals(25, ExamScoreConverter.toQuestionScore(72, 100, 35));
        assertEquals(25, ExamScoreConverter.toQuestionScore(100, 100, 25));
    }

    @Test
    void boundsInvalidRawScoreBeforeConversion() {
        assertEquals(0, ExamScoreConverter.toQuestionScore(-10, 100, 35));
        assertEquals(35, ExamScoreConverter.toQuestionScore(150, 100, 35));
    }
}
