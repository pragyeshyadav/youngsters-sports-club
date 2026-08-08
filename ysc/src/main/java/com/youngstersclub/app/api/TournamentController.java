package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.TournamentRegistrationRequest;
import com.youngstersclub.app.dto.TournamentResponse;
import com.youngstersclub.app.service.TournamentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.youngstersclub.app.dto.TournamentRegistrationResult;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<TournamentResponse>> getActiveSummerOlympicsEvents(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(tournamentService.getActiveSummerOlympicsEvents(actorEmail));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUserForTournaments(
            @RequestBody TournamentRegistrationRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        if (request.getUserId() == null || request.getTournamentIds() == null || request.getTournamentIds().isEmpty()) {
            return ResponseEntity.badRequest().body("User ID and at least one tournament are required.");
        }
        TournamentRegistrationResult result = tournamentService.registerUserForTournaments(
                request.getUserId(),
                request.getTournamentIds(),
                actorEmail);
        return ResponseEntity.ok(result);
    }
}
