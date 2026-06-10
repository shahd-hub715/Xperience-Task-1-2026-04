package com.xperience.hero.repository;

import com.xperience.hero.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    boolean existsByEvent_IdAndEmail(Long eventId, String email);
}
