package com.xperience.hero.dto;

public record RsvpView(
        Long invitationId,
        String email,
        String eventTitle,
        String status,
        boolean locked
) {}
