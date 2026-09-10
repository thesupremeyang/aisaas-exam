package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedInitialAssessmentClientTest {
    @Test
    void createsA100PointRubricReportForCompleteSubmissionMaterial() {
        var report = new RuleBasedInitialAssessmentClient().assess("{\"scene\":\"政务服务\",\"function\":\"政策问答\",\"technical\":\"RAG\",\"evidence\":\"演示视频\"}");

        assertEquals(85, report.totalScore());
        assertEquals(5, report.dimensionScores().size());
    }
}
