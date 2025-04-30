// domain/UserTokenStat.java
package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("user_token_stats")
public class UserTokenStat {
    @Id
    private String id;          // e.g. userId + "|" + windowEnd
    private String userId;
    private Instant windowEnd;
    private long tokenCount;
}
