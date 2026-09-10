package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentScoreValidatorTest {
    @Test
    void acceptsScoresThatMatchEveryRubricMaximumAndTotal() {
        var scores = Map.of("SCENE_VALUE", 18, "FUNCTION_COMPLETENESS", 18, "INNOVATION", 16, "TECHNICAL_DESCRIPTION", 20, "VERIFIABILITY", 20);
        assertDoesNotThrow(() -> AssessmentScoreValidator.validate(scores, 92));
    }

    @Test
    void rejectsScoreOverDimensionMaximum() {
        var scores = Map.of("SCENE_VALUE", 21, "FUNCTION_COMPLETENESS", 18, "INNOVATION", 16, "TECHNICAL_DESCRIPTION", 20, "VERIFIABILITY", 20);
        assertThrows(IllegalArgumentException.class, () -> AssessmentScoreValidator.validate(scores, 95));
    }
}
