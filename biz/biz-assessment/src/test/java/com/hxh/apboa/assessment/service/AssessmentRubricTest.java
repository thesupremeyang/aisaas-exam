package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessmentRubricTest {
    @Test
    void uses100PointDefaultRubricWhenConfigMissing() {
        var rubric = AssessmentRubric.fromCampaignConfig(null);

        assertEquals(100, rubric.totalScore());
        assertEquals("EXCELLENT", rubric.levelOf(90));
        assertEquals("GOOD", rubric.levelOf(75));
        assertEquals("PASS", rubric.levelOf(60));
    }

    @Test
    void readsDimensionAndLevelThresholdsFromCampaignConfig() {
        var rubric = AssessmentRubric.fromCampaignConfig("""
                {"rubric":{"dimensions":{"SCENE_VALUE":18,"FUNCTION_COMPLETENESS":22,"INNOVATION":20,"TECHNICAL_DESCRIPTION":30,"VERIFIABILITY":30},"levels":{"EXCELLENT":105,"GOOD":88,"PASS":70}}}
                """);

        assertEquals(120, rubric.totalScore());
        assertEquals("GOOD", rubric.levelOf(90));
        assertEquals(18, rubric.maxScores().get("SCENE_VALUE"));
    }
}
