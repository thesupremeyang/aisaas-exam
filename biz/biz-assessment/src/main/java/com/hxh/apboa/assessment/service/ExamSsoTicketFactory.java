package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamSsoTicket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Creates opaque five-minute credentials; only a SHA-256 digest is persisted. */
public final class ExamSsoTicketFactory {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public IssuedTicket issue(Long tenantId, Long accountId, Long campaignId, String returnUrl, LocalDateTime issuedAt) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawTicket = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        var ticket = new ExamSsoTicket();
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setCampaignId(campaignId);
        ticket.setReturnUrl(returnUrl);
        ticket.setJti(UUID.randomUUID().toString());
        ticket.setTicketHash(hash(rawTicket));
        ticket.setExpiresAt(issuedAt.plusMinutes(5));
        return new IssuedTicket(ticket, rawTicket);
    }

    public String hash(String rawTicket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawTicket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    public record IssuedTicket(ExamSsoTicket ticket, String rawTicket) {
    }
}
