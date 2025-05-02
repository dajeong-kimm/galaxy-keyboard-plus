package com.ssafy.logging.dto;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("global_token_stats")
public class GlobalTokenStat {
  @Id
  private String windowEnd;
  private Instant timestamp;
  private long totalCount;
}
