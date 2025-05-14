package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// ServerRanking.java
@Data
@Document("server_ranking")
public class ServerRanking {
    @Id
    private String id;           // e.g. "DAILY|2025-04-28|serverA"
    private String periodType;       // "DAILY" or "WEEKLY"
    private String periodKey;        // date or weekStart
    private String serverId;
    private long callCount;
    private int rank;
}