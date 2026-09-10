package com.hxh.apboa.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssessmentPromptBuilderTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void includesCampaignRubricAndExcludesApplicantSensitiveFields() throws Exception {
        var rubric = AssessmentRubric.fromCampaignConfig("""
                {"rubric":{"dimensions":{"SCENE_VALUE":18,"FUNCTION_COMPLETENESS":22},"levels":{"GOOD":30,"PASS":20}}}
                """);

        var payload = new AssessmentPromptBuilder(json).build("""
                {"applicant":{"name":"张三","identityNumber":"110101199001011234","phone":"13800000000"},"work":{"title":"政务问答"}}
                """, rubric);
        var root = json.readTree(payload);

        assertEquals(18, root.path("rubric").path("dimensions").path("SCENE_VALUE").asInt());
        assertEquals("政务问答", root.path("submission").path("work").path("title").asText());
        assertFalse(root.path("submission").path("applicant").has("identityNumber"));
        assertFalse(root.path("submission").path("applicant").has("phone"));
    }
}
