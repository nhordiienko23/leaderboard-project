package com.github.nhordiienko23.leaderboardservice.controller;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardEntryController {
    private final LeaderboardService leaderboardService;
    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@RequestParam(defaultValue = "10") int limit){
        return ResponseEntity.ok(leaderboardService.getLeaderboard(limit));
    }
}
