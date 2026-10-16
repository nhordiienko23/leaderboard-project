package com.github.nhordiienko23.leaderboardservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeaderboardServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaderboardServiceApplication.class, args);
	}

}
