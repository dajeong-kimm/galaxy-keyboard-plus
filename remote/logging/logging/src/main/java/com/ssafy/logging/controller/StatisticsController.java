package com.ssafy.logging.controller;

import com.ssafy.logging.domain.DailyTokenStat;
import com.ssafy.logging.domain.ServerRanking;
import com.ssafy.logging.domain.WeeklyTokenStat;
import com.ssafy.logging.dto.GlobalTokenStat;
import com.ssafy.logging.domain.TokenUsageLog;
import com.ssafy.logging.repository.DailyTokenStatRepository;
import com.ssafy.logging.repository.ServerRankingRepository;
import com.ssafy.logging.repository.TokenUsageRepository;
import com.ssafy.logging.repository.WeeklyTokenStatRepository;
import com.ssafy.logging.scheduler.StatisticsScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatisticsController {
    private final DailyTokenStatRepository dailyTokenRepo;
    private final WeeklyTokenStatRepository weeklyTokenRepo;
    private final ServerRankingRepository rankingRepo;
    private final TokenUsageRepository tokenRepo;
    private final StatisticsScheduler scheduler;

    /** 일별 사용자 토큰 사용량 */
    @GetMapping("/tokens/daily/{date}")
    public Flux<DailyTokenStat> dailyToken(@PathVariable String date) {
        return dailyTokenRepo.findByDate(date);
    }

    /** 주별 사용자 토큰 사용량 */
    @GetMapping("/tokens/weekly/{weekStart}")
    public Flux<WeeklyTokenStat> weeklyToken(@PathVariable String weekStart) {
        return weeklyTokenRepo.findByWeekStart(weekStart);
    }

    /**
     * 실시간 전체 일별 토큰 사용량
     * @return 오늘 00:00 부터 지금까지 사용된 토큰 총합
     */
    @GetMapping("/tokens/realtime/daily")
    public Mono<GlobalTokenStat> getRealtimeDaily() {
        Instant now = Instant.now();
        // 오늘 00:00 (서버 기본 타임존 기준)
        Instant startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        return tokenRepo.findByTimestampBetween(startOfDay, now)
                .map(TokenUsageLog::getTokenCount)
                .reduce(0L, Long::sum)
                .map(total -> {
                    GlobalTokenStat stat = new GlobalTokenStat();
                    stat.setWindowEnd(now.toString());
                    stat.setTimestamp(now);
                    stat.setTotalCount(total);
                    return stat;
                });
    }

    /**
     * 실시간 전체 주별 토큰 사용량
     * @return 이번 주 월요일 00:00 부터 지금까지 사용된 토큰 총합
     */
    @GetMapping("/tokens/realtime/weekly")
    public Mono<GlobalTokenStat> getRealtimeWeekly() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        // 이번 주 월요일
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant startOfWeek = monday.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return tokenRepo.findByTimestampBetween(startOfWeek, now)
                .map(TokenUsageLog::getTokenCount)
                .reduce(0L, Long::sum)
                .map(total -> {
                    GlobalTokenStat stat = new GlobalTokenStat();
                    stat.setWindowEnd(now.toString());
                    stat.setTimestamp(now);
                    stat.setTotalCount(total);
                    return stat;
                });
    }

    /** 스케줄러로 미리 계산해둔 통계를 바로 다시 실행하고 싶을 때 */
    @PostMapping("/run-daily")
    public Mono<Void> runDailyStatsNow() {
        return Mono.fromRunnable(scheduler::dailyJob);
    }

    /** 일별 MCP 서버 순위 */
    @GetMapping("/servers/daily/{date}")
    public Flux<ServerRanking> dailyServerRanking(@PathVariable String date) {
        return rankingRepo.findByPeriodTypeAndPeriodKeyOrderByRank("DAILY", date);
    }

    /** 주별 MCP 서버 순위 */
    @GetMapping("/servers/weekly/{weekStart}")
    public Flux<ServerRanking> weeklyServerRanking(@PathVariable String weekStart) {
        return rankingRepo.findByPeriodTypeAndPeriodKeyOrderByRank("WEEKLY", weekStart);
    }
}
