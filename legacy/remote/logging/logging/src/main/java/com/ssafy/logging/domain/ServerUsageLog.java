package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// ServerUsageLog.java
@Data
@Document("server_usage_log")
public class ServerUsageLog {
    @Id
    private String id;
    private String serverId;
    private Instant timestamp;
}
