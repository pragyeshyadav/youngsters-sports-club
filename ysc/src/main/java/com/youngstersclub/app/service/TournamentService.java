package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TournamentResponse;
import com.youngstersclub.app.entity.Tournament;
import com.youngstersclub.app.entity.TournamentRegistration;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.TournamentRegistrationRepository;
import com.youngstersclub.app.repository.TournamentRepository;
import com.youngstersclub.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.youngstersclub.app.dto.TournamentRegistrationResult;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final UserRepository userRepository;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentRegistrationRepository registrationRepository,
                             UserRepository userRepository) {
        this.tournamentRepository = tournamentRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
    }

    public List<TournamentResponse> getActiveSummerOlympicsEvents() {
        return tournamentRepository.findByEventNameAndIsActiveTrue("Summer Olympics 2K26")
                .stream()
                .map(t -> new TournamentResponse(
                        t.getId(),
                        t.getName(),
                        t.getEventName(),
                        t.getRegistrationFee()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public TournamentRegistrationResult registerUserForTournaments(Integer userId, List<Long> tournamentIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        TournamentRegistrationResult result = new TournamentRegistrationResult();

        for (Long tournamentId : tournamentIds) {
            Tournament tournament = tournamentRepository.findById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            boolean isAlreadyRegistered = registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId);

            if (isAlreadyRegistered) {
                result.getAlreadyRegistered().add(tournament.getName());
            } else {
                TournamentRegistration registration = new TournamentRegistration();
                registration.setTournament(tournament);
                registration.setUser(user);
                registrationRepository.save(registration);
                
                result.getSuccessfullyRegistered().add(tournament.getName());
            }
        }
        return result;
    }
}
