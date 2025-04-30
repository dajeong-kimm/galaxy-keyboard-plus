// domain/GlobalTokenStat.java
package com.ssafy.logging.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("global_token_stats")
public class GlobalTokenStat {
    @Id
    private String windowEnd;   // ISO_INSTANT 문자열
    private Instant timestamp;  // 윈도우 구간의 끝 시각
    private long totalCount;    // 전체 사용자 합계 토큰 사용량
}
