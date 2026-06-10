package com.xperience.hero.dto;

import java.time.LocalDateTime;

public record EventView(
        Long id,
        String title,
        LocalDateTime eventDateTime,
        String location,
        Integer maxCapacity,
        String status
) {}
