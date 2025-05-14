package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// WeeklyTokenStat.java
@Data
@Document("weekly_token_stats")
public class WeeklyTokenStat {
    @Id
    private String id;        // e.g. "2025-04-27|user123"
    private String weekStart;     // 해당 주 월요일 날짜 "YYYY-MM-DD"
    private String userId;
    private long totalCount;
}