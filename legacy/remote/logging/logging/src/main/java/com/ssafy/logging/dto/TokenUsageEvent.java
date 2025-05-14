package com.ssafy.logging.dto;
import lombok.Data;

@Data
public class TokenUsageEvent {
    private String userId;
    private long tokenCount;
    private long timestamp;
}