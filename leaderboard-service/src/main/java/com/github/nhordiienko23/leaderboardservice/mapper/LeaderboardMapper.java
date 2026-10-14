package com.github.nhordiienko23.leaderboardservice.mapper;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LeaderboardMapper {

    LeaderboardEntryDTO toDTO(LeaderboardEntry entry);
}
