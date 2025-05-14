package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// DailyTokenStat.java
@Data
@Document("daily_token_stats")
public class DailyTokenStat {
    @Id
    private String id;        // e.g. "2025-04-28|user123"
    private String date;          // "YYYY-MM-DD"
    private String userId;
    private long totalCount;
}