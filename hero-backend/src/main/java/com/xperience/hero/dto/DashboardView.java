package com.xperience.hero.dto;

public record DashboardView(
        Long eventId,
        String eventTitle,
        String status,
        long confirmed,
        long waitlisted,
        long declined,
        long maybe,
        long noResponse,
        Integer maxCapacity
) {}
