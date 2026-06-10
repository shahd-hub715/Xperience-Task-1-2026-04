package com.xperience.hero.repository;

import com.xperience.hero.entity.Rsvp;
import com.xperience.hero.entity.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {

    Optional<Rsvp> findByInvitation_Id(Long invitationId);

    long countByInvitation_Event_IdAndStatus(Long eventId, RsvpStatus status);

    // FIFO: promote by the time they were placed on waitlist (updatedAt ascending)
    List<Rsvp> findByInvitation_Event_IdAndStatusOrderByUpdatedAtAsc(Long eventId, RsvpStatus status);
}
