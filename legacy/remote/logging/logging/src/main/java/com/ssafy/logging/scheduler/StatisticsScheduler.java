package com.ssafy.logging.scheduler;

import com.ssafy.logging.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ssafy.logging.domain.*;

@Component
@RequiredArgsConstructor
public class StatisticsScheduler {
    private final TokenUsageRepository tokenRepo;
    private final ServerUsageRepository serverRepo;
    private final DailyTokenStatRepository dailyTokenRepo;
    private final WeeklyTokenStatRepository weeklyTokenRepo;
    private final ServerRankingRepository rankingRepo;

    // 매일 00:10에 전일 통계 집계
    @Scheduled(cron = "0 10 0 * * *")
    public void dailyJob() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Instant start = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String dateKey = yesterday.toString();

        // 토큰 일별 합계
        tokenRepo.findByTimestampBetween(start, end)
            .groupBy(TokenUsageLog::getUserId)
            .flatMap(g -> g.map(TokenUsageLog::getTokenCount).reduce(Long::sum)
                .map(sum -> {
                    DailyTokenStat stat = new DailyTokenStat();
                    stat.setId(dateKey + "|" + g.key());
                    stat.setDate(dateKey);
                    stat.setUserId(g.key());
                    stat.setTotalCount(sum);
                    return stat;
                }))
            .flatMap(dailyTokenRepo::save)
            .subscribe();

        // 서버 일별 호출 횟수 & 순위
        serverRepo.findByTimestampBetween(start, end)
                .map(ServerUsageLog::getServerId)
                .collectList()
                .flatMapMany(list -> {
                    // 1) Map<String,Long> 으로 타입을 명확히 지정
                    Map<String, Long> counts = list.stream()
                            .collect(Collectors.groupingBy(
                                    Function.identity(),      // s -> s 대신
                                    Collectors.counting()
                            ));

                    // 2) ServerRanking 객체 리스트 생성
                    List<ServerRanking> rankingList = counts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                            .map(e -> {
                                ServerRanking r = new ServerRanking();
                                r.setId("DAILY|" + dateKey + "|" + e.getKey());
                                r.setPeriodType("DAILY");
                                r.setPeriodKey(dateKey);
                                r.setServerId(e.getKey());
                                r.setCallCount(e.getValue());
                                return r;
                            })
                            .collect(Collectors.toList());

                    // 3) 리스트를 Flux로 변환
                    return Flux.fromIterable(rankingList);
                })
                // 4) zipWith에도 제네릭을 명시
                .zipWith(Flux.range(1, Integer.MAX_VALUE),
                        (ServerRanking r, Integer idx) -> {
                            r.setRank(idx);
                            return r;
                        }
                )
                .flatMap(rankingRepo::save)
                .subscribe();
    }

    // 매주 월요일 00:20에 전주 통계 집계 (주 단위 월요일 기준)
    @Scheduled(cron = "0 20 0 * * MON")
    public void weeklyJob() {
        LocalDate today = LocalDate.now();
        LocalDate prevMonday = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        Instant start = prevMonday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        String weekKey = prevMonday.toString();

        // 토큰 주별 합계
        tokenRepo.findByTimestampBetween(start, end)
            .groupBy(TokenUsageLog::getUserId)
            .flatMap(g -> g.map(TokenUsageLog::getTokenCount).reduce(Long::sum)
                .map(sum -> {
                    WeeklyTokenStat stat = new WeeklyTokenStat();
                    stat.setId(weekKey + "|" + g.key());
                    stat.setWeekStart(weekKey);
                    stat.setUserId(g.key());
                    stat.setTotalCount(sum);
                    return stat;
                }))
            .flatMap(weeklyTokenRepo::save)
            .subscribe();

        // 서버 주별 순위
        serverRepo.findByTimestampBetween(start, end)
                .map(ServerUsageLog::getServerId)
                .collectList()
                .flatMapMany(list -> {
                    // 1) Map<String, Long> 으로 명확히 타입 지정
                    Map<String, Long> counts = list.stream()
                            .collect(Collectors.groupingBy(
                                    Function.identity(),    // s -> s 대신 Function.identity()
                                    Collectors.counting()
                            ));

                    // 2) ServerRanking 리스트 생성
                    List<ServerRanking> rankingList = counts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                            .map(e -> {
                                ServerRanking r = new ServerRanking();
                                r.setId("WEEKLY|" + weekKey + "|" + e.getKey());
                                r.setPeriodType("WEEKLY");
                                r.setPeriodKey(weekKey);
                                r.setServerId(e.getKey());
                                r.setCallCount(e.getValue());
                                return r;
                            })
                            .collect(Collectors.toList());

                    // 3) Flux<ServerRanking> 으로 변환
                    return Flux.fromIterable(rankingList);
                })
                // 4) zipWith도 제네릭 명시
                .zipWith(Flux.range(1, Integer.MAX_VALUE),
                        (ServerRanking r, Integer idx) -> {
                            r.setRank(idx);
                            return r;
                        })
                .flatMap(rankingRepo::save)
                .subscribe();
    }
}
