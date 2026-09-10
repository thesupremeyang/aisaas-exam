package com.hxh.apboa.assessment.service;

@FunctionalInterface
public interface RuntimeAssessmentGateway {
    String invoke(String requestJson);
}
