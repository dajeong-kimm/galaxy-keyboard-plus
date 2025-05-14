package com.ssafy.logging.dto;

import lombok.Data;

@Data
public class ServerUsageEvent {
    private String serverId;
    private long timestamp;
}