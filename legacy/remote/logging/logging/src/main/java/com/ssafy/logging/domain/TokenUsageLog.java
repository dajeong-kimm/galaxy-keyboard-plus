// TokenUsageLog.java
package com.ssafy.logging.domain;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("token_usage_log")
public class TokenUsageLog {
    @Id private String id;
    private String userId;
    private long tokenCount;
    private Instant timestamp;
}