package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssessmentTaskStateMachineTest {
    @Test
    void allowsQueuedTaskToStartAndComplete() {
        assertDoesNotThrow(() -> AssessmentTaskStateMachine.requireTransition("QUEUED", "RUNNING"));
        assertDoesNotThrow(() -> AssessmentTaskStateMachine.requireTransition("RUNNING", "SUCCEEDED"));
    }

    @Test
    void rejectsCompletingTaskThatWasNeverStarted() {
        assertThrows(IllegalStateException.class,
                () -> AssessmentTaskStateMachine.requireTransition("QUEUED", "SUCCEEDED"));
    }
}
