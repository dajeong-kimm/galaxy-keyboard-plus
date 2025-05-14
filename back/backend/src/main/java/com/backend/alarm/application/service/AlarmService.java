package com.backend.alarm.application.service;

import com.backend.alarm.application.in.FcmTokenInDto;
import com.backend.alarm.domain.entity.FcmToken;
import com.backend.alarm.repository.FcmTokenRepository;
import com.backend.plan.domain.entity.Plan;
import com.backend.plan.repository.PlanRepository;
import com.backend.user.domain.entity.User;
import com.backend.user.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlarmService {
    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    public FcmToken saveFcmToken(FcmTokenInDto inDto){

        UUID userId = inDto.getUserId();
        String token = inDto.getFcmToken();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 userId의 사용자가 존재하지 않습니다."));

        Optional<FcmToken> existingToken = fcmTokenRepository.findByToken(token);
        Optional<FcmToken> existingUser = fcmTokenRepository.findByUser_UserId(userId);

        // 기존의 같은 유저, 같은 토큰이 있는 경우 → 삭제하지 않고 그대로 유지
        if (existingUser.isPresent() && existingUser.get().getToken().equals(token)) {
            return existingUser.get();
        }

        // 기존 토큰이 있지만 해당 유저와 다르면 → 기존 토큰 삭제 후, 해당 유저의 데이터만 유지
        if (existingToken.isPresent() && !existingToken.get().getUser().getUserId().equals(userId)) {
            FcmToken updatedUserToken = existingToken.get();
            updatedUserToken.changeUser(user);
            return fcmTokenRepository.save(updatedUserToken);
        }

        // 기존 유저가 있지만, 기존 토큰과 다르면 → 토큰 값 업데이트
        if (existingUser.isPresent()) {
            FcmToken updatedUserToken = existingUser.get();
            updatedUserToken.changeToken(token);
            return fcmTokenRepository.save(updatedUserToken);
        }

        // 기존의 없는 유저, 없는 토큰일 때만 새로 추가
        FcmToken newToken = FcmToken.builder()
                .fcmTokenId(UUID.randomUUID())
                .user(user)
                .token(token)
                .build();
        return fcmTokenRepository.save(newToken);

    }


    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void sendScheduledAlarms() {
        LocalDateTime now = LocalDateTime.now();

        List<Plan> plans = planRepository.findAllByAlarmTfTrue();

        for (Plan plan : plans) {
            LocalDateTime planTime = plan.getPlanTime();

            if (planTime.isBefore(now)) {
                // 이미 지난 일정
                plan.setAlarmTf(false);
                planRepository.save(plan);
                continue;
            }

            long minutesDiff = Duration.between(now, planTime).toMinutes();

            //1시간 전, 24시간 전에 알림 전송
            if (minutesDiff == 60 || minutesDiff == 24 * 60) {
                sendPush(plan);
            }
        }
    }

    public void sendPush(Plan plan) {
        User user = plan.getUser();
        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser_UserId(user.getUserId());

        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime planTime = plan.getPlanTime();
        long minutesDiff = Duration.between(now, planTime).toMinutes();

        String title = "일정 알림";
        String body;

        if (minutesDiff >= 1439 && minutesDiff <= 1441) {
            body = String.format("'%s' 내일 이 시간에 일정이 있어요!", plan.getPlanContent());
        } else if (minutesDiff >= 59 && minutesDiff <= 61) {
            body = String.format("'%s' 일정이 한 시간 후에 시작돼요!", plan.getPlanContent());
        } else {
            return; // 조건에 맞지 않으면 전송하지 않음
        }

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("FCM 전송 성공: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("FCM 전송 실패: " + e.getMessage());
        }
    }


}
