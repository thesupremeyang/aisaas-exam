package com.hxh.apboa.assessment.service;

import java.util.Map;
import java.util.Set;

/** 约束 AI 初评任务的状态转移，防止任务被跳过执行直接产出报告。 */
public final class AssessmentTaskStateMachine {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "QUEUED", Set.of("RUNNING", "CANCELLED"),
            "RUNNING", Set.of("SUCCEEDED", "FAILED"),
            "FAILED", Set.of("QUEUED")
    );

    private AssessmentTaskStateMachine() {
    }

    public static void requireTransition(String current, String target) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("不允许将任务状态从 " + current + " 变更为 " + target);
        }
    }
}
