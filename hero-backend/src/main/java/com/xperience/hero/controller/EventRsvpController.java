package com.xperience.hero.controller;

import com.xperience.hero.dto.*;
import com.xperience.hero.service.EventRsvpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventRsvpController {

    private final EventRsvpService service;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventView createEvent(@RequestBody CreateEventRequest req) {
        return service.createEvent(req);
    }

    @PostMapping("/events/{eventId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteView invite(@PathVariable Long eventId, @RequestBody InviteRequest req) {
        return service.invite(eventId, req.email());
    }

    @GetMapping("/rsvp/{token}")
    public RsvpView getRsvp(@PathVariable String token) {
        return service.getRsvpByToken(token);
    }

    @PutMapping("/rsvp/{token}")
    public RsvpView submitRsvp(@PathVariable String token, @RequestBody SubmitRsvpRequest req) {
        return service.submitRsvp(token, req.response());
    }

    @GetMapping("/events/{eventId}/dashboard")
    public DashboardView getDashboard(@PathVariable Long eventId) {
        return service.getDashboard(eventId);
    }
}
