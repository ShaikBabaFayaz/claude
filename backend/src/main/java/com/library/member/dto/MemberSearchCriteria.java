package com.library.member.dto;

import com.library.common.error.FieldErrorDto;
import com.library.common.web.QueryParams;
import com.library.member.MemberStatus;
import java.util.Arrays;
import java.util.List;

/**
 * Validated filters of {@code GET /api/members} (AC-3.BE.8, D9, DR-18). A null component means "no filter". The status
 * must be exactly {@code ACTIVE} or {@code INACTIVE}; {@code search} is trimmed and at most 254 characters, matched as a
 * case-insensitive "contains" on name or email.
 */
public record MemberSearchCriteria(MemberStatus status, String search) {

    /** Maximum length of the {@code search} text (DR-18); equals the longest storable email. */
    public static final int MAX_SEARCH_LENGTH = 254;

    public static MemberSearchCriteria none() {
        return new MemberSearchCriteria(null, null);
    }

    /** Adds one field error per rejected parameter to {@code errors}; rejected parameters are treated as absent. */
    public static MemberSearchCriteria parse(String rawStatus, String rawSearch, List<FieldErrorDto> errors) {
        MemberStatus status = QueryParams.option(
                "status", rawStatus,
                value -> Arrays.stream(MemberStatus.values()).filter(s -> s.name().equals(value)).findFirst(),
                "ACTIVE, INACTIVE", errors);
        String search = QueryParams.text("search", rawSearch, MAX_SEARCH_LENGTH, errors);
        return new MemberSearchCriteria(status, search);
    }
}
