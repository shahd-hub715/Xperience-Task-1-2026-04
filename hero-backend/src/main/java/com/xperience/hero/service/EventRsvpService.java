package com.xperience.hero.service;

import com.xperience.hero.dto.*;
import com.xperience.hero.entity.*;
import com.xperience.hero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventRsvpService {

    private final EventRepository eventRepository;
    private final InvitationRepository invitationRepository;
    private final RsvpRepository rsvpRepository;

    public EventView createEvent(CreateEventRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (req.eventDateTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventDateTime is required");
        }

        Event event = new Event();
        event.setTitle(req.title());
        event.setDescription(req.description());
        event.setEventDateTime(req.eventDateTime());
        event.setLocation(req.location());
        event.setMaxCapacity(req.maxCapacity());
        eventRepository.save(event);

        return toEventView(event);
    }

    @Transactional
    public InviteView invite(Long eventId, String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }

        Event event = requireEvent(eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is cancelled");
        }

        if (invitationRepository.existsByEvent_IdAndEmail(eventId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email has already been invited");
        }

        Invitation invitation = new Invitation();
        invitation.setEvent(event);
        invitation.setEmail(email);
        invitation.setToken(UUID.randomUUID().toString());
        invitationRepository.save(invitation);

        // Create initial NO_RESPONSE record so every invitation has an Rsvp row
        Rsvp rsvp = new Rsvp();
        rsvp.setInvitation(invitation);
        rsvpRepository.save(rsvp);

        String rsvpLink = "http://localhost:8280/api/rsvp/" + invitation.getToken();
        return new InviteView(invitation.getId(), email, invitation.getToken(), rsvpLink);
    }

    public RsvpView getRsvpByToken(String token) {
        Invitation invitation = requireInvitationByToken(token);
        Rsvp rsvp = requireRsvp(invitation.getId());
        Event event = invitation.getEvent();

        return new RsvpView(
                invitation.getId(),
                invitation.getEmail(),
                event.getTitle(),
                rsvp.getStatus().name(),
                isLocked(event)
        );
    }

    @Transactional
    public RsvpView submitRsvp(String token, String responseStr) {
        Invitation invitation = requireInvitationByToken(token);
        Event event = invitation.getEvent();

        if (isLocked(event)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RSVPs are locked: event has started or is closed/cancelled");
        }

        RsvpStatus requested = parseResponse(responseStr);

        Rsvp rsvp = requireRsvp(invitation.getId());
        RsvpStatus previous = rsvp.getStatus();

        // Capacity check: YES that exceeds capacity becomes WAITLISTED
        if (requested == RsvpStatus.CONFIRMED && previous != RsvpStatus.CONFIRMED) {
            if (event.getMaxCapacity() != null) {
                long confirmedCount = rsvpRepository.countByInvitation_Event_IdAndStatus(
                        event.getId(), RsvpStatus.CONFIRMED);
                if (confirmedCount >= event.getMaxCapacity()) {
                    requested = RsvpStatus.WAITLISTED;
                }
            }
        }

        rsvp.setStatus(requested);
        rsvp.setUpdatedAt(LocalDateTime.now());
        rsvpRepository.save(rsvp);

        // Waitlist promotion (IS2): CONFIRMED → DECLINED vacates one spot; promote first in line
        if (previous == RsvpStatus.CONFIRMED && requested == RsvpStatus.DECLINED) {
            List<Rsvp> waitlisted = rsvpRepository
                    .findByInvitation_Event_IdAndStatusOrderByUpdatedAtAsc(event.getId(), RsvpStatus.WAITLISTED);
            if (!waitlisted.isEmpty()) {
                Rsvp promoted = waitlisted.get(0);
                promoted.setStatus(RsvpStatus.CONFIRMED);
                promoted.setUpdatedAt(LocalDateTime.now());
                rsvpRepository.save(promoted);
            }
        }

        return new RsvpView(
                invitation.getId(),
                invitation.getEmail(),
                event.getTitle(),
                rsvp.getStatus().name(),
                false
        );
    }

    public DashboardView getDashboard(Long eventId) {
        Event event = requireEvent(eventId);

        return new DashboardView(
                eventId,
                event.getTitle(),
                event.getStatus().name(),
                rsvpRepository.countByInvitation_Event_IdAndStatus(eventId, RsvpStatus.CONFIRMED),
                rsvpRepository.countByInvitation_Event_IdAndStatus(eventId, RsvpStatus.WAITLISTED),
                rsvpRepository.countByInvitation_Event_IdAndStatus(eventId, RsvpStatus.DECLINED),
                rsvpRepository.countByInvitation_Event_IdAndStatus(eventId, RsvpStatus.MAYBE),
                rsvpRepository.countByInvitation_Event_IdAndStatus(eventId, RsvpStatus.NO_RESPONSE),
                event.getMaxCapacity()
        );
    }

    // --- helpers ---

    private boolean isLocked(Event event) {
        return LocalDateTime.now().isAfter(event.getEventDateTime())
                || event.getStatus() == EventStatus.CLOSED
                || event.getStatus() == EventStatus.CANCELLED;
    }

    private RsvpStatus parseResponse(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "response is required");
        }
        return switch (raw.trim().toUpperCase()) {
            case "YES" -> RsvpStatus.CONFIRMED;
            case "NO" -> RsvpStatus.DECLINED;
            case "MAYBE" -> RsvpStatus.MAYBE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "response must be YES, NO, or MAYBE");
        };
    }

    private Event requireEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event not found: " + eventId));
    }

    private Invitation requireInvitationByToken(String token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid token"));
    }

    private Rsvp requireRsvp(Long invitationId) {
        return rsvpRepository.findByInvitation_Id(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "RSVP record missing for invitation " + invitationId));
    }

    private EventView toEventView(Event e) {
        return new EventView(e.getId(), e.getTitle(), e.getEventDateTime(),
                e.getLocation(), e.getMaxCapacity(), e.getStatus().name());
    }
}
