package com.hxh.apboa.assessment.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamSsoReturnUrlPolicyTest {

    @Test
    void acceptsRelativePortalRouteAndConfiguredHttpsOrigin() {
        var policy = new ExamSsoReturnUrlPolicy(Set.of("https://exam.example.com"));

        assertDoesNotThrow(() -> policy.validate("/exam/candidate/portal?campaignId=100"));
        assertDoesNotThrow(() -> policy.validate("https://exam.example.com/exam/candidate/portal"));
    }

    @Test
    void rejectsOpenRedirectAndNonHttpsConfiguredHost() {
        var policy = new ExamSsoReturnUrlPolicy(Set.of("https://exam.example.com"));

        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://evil.example/steal"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("//evil.example/steal"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("http://exam.example.com/unsafe"));
    }
}
