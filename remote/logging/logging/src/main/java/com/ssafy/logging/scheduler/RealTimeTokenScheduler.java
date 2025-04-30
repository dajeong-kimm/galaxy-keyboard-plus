// scheduler/RealTimeTokenScheduler.java
package com.ssafy.logging.scheduler;

import com.ssafy.logging.domain.UserTokenStat;
import com.ssafy.logging.domain.GlobalTokenStat;
import com.ssafy.logging.repository.TokenUsageRepository;
import com.ssafy.logging.repository.UserTokenStatRepository;
import com.ssafy.logging.repository.GlobalTokenStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeTokenScheduler {

    private final TokenUsageRepository tokenRepo;
    private final UserTokenStatRepository userStatRepo;
    private final GlobalTokenStatRepository globalStatRepo;

    /**
     * 1분마다 사용자별 + 전체 토큰 사용량 집계
     */
    @Scheduled(fixedRate = 60_000)
    public void updateTokenStats() {
        Instant now = Instant.now();
        Instant oneMinAgo = now.minusSeconds(60);
        String windowKey = DateTimeFormatter.ISO_INSTANT.format(now);

        // 1) 최근 1분치 로그 모두 조회
        tokenRepo.findByTimestampBetween(oneMinAgo, now)
                .collectList()
                .flatMapMany(list -> {
                    // 2) 전체 합계 계산
                    long total = list.stream()
                            .mapToLong(log -> log.getTokenCount())
                            .sum();
                    GlobalTokenStat g = new GlobalTokenStat();
                    g.setWindowEnd(windowKey);
                    g.setTimestamp(now);
                    g.setTotalCount(total);

                    // 3) 사용자별 합계 계산
                    Map<String, Long> perUser = list.stream()
                            .collect(Collectors.groupingBy(
                                    log -> log.getUserId(),
                                    Collectors.summingLong(log -> log.getTokenCount())
                            ));

                    // 4) Flux에 글로벌 + 유저별 순서로 담기
                    Flux<Object> allStats = Flux.concat(
                            globalStatRepo.save(g).doOnError(e -> {/* 로깅 */}),
                            Flux.fromIterable(perUser.entrySet())
                                    .map(e -> {
                                        UserTokenStat u = new UserTokenStat();
                                        u.setId(e.getKey() + "|" + windowKey);
                                        u.setUserId(e.getKey());
                                        u.setWindowEnd(now);
                                        u.setTokenCount(e.getValue());
                                        return u;
                                    })
                                    .flatMap(userStatRepo::save)
                                    .doOnError(e -> log.error("실시간 토큰 통계 저장 중 에러 발생 (windowEnd={}): {}", windowKey, e.getMessage(), e))
                    );
                    return allStats;
                })
                .subscribe();  // 실제 저장 트리거
    }
}
