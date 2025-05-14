package com.ssafy.logging.controller;

import com.ssafy.logging.domain.DailyTokenStat;
import com.ssafy.logging.domain.ServerRanking;
import com.ssafy.logging.domain.WeeklyTokenStat;
import com.ssafy.logging.repository.DailyTokenStatRepository;
import com.ssafy.logging.repository.ServerRankingRepository;
import com.ssafy.logging.repository.WeeklyTokenStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatisticsController {
    private final DailyTokenStatRepository dailyTokenRepo;
    private final WeeklyTokenStatRepository weeklyTokenRepo;
    private final ServerRankingRepository rankingRepo;

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
