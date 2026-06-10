package com.xperience.hero.dto;

public record InviteView(
        Long invitationId,
        String email,
        String token,
        String rsvpLink
) {}
