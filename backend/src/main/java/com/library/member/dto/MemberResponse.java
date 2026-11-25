package com.library.member.dto;

import com.library.member.Member;
import com.library.member.MemberStatus;

/** Response shape of every member endpoint (architecture 6.1). */
public record MemberResponse(Long id, String name, String email, MemberStatus status, int activeLoanCount) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(), member.getName(), member.getEmail(), member.getStatus(), member.getActiveLoanCount());
    }
}
