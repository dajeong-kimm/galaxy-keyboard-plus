package com.backend.plan.application.service;

import com.backend.global.common.exception.BaseException;
import com.backend.global.common.response.BaseResponseStatus;
import com.backend.image.application.out.ImageThumbnailOutDto;
import com.backend.image.domain.entity.Image;
import com.backend.image.repository.ImageRepository;
import com.backend.plan.application.in.PlanAlarmToggleInDto;
import com.backend.plan.application.in.SavePlanInDto;
import com.backend.plan.application.out.ImageInfo;
import com.backend.plan.application.out.PlanDetailOutDto;
import com.backend.plan.application.out.PlanThumbnailOutDto;
import com.backend.plan.application.out.SavePlanOutDto;
import com.backend.plan.domain.entity.Plan;
import com.backend.plan.repository.PlanRepository;
import com.backend.user.domain.entity.User;
import com.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final ModelMapper modelMapper;

    public SavePlanOutDto savePlan(SavePlanInDto inDto) {

        User user = userRepository.findById(inDto.getUserId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));

        Image image = imageRepository.findById(inDto.getImageId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_IMAGE));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
        LocalDateTime planTime = LocalDateTime.parse(inDto.getPlanTime(), formatter);

        Plan plan = Plan.builder()
                .planId(UUID.randomUUID())
                .user(user)
                .planTime(planTime)
                .planContent(inDto.getPlanContent())
                .image(image)
                .alarmTf(true) //builder 쓰면 default 무시되기 때문에 한 번 더 등록
                .build();

        plan = planRepository.save(plan);

        return new SavePlanOutDto(
                plan.getPlanId(),
                plan.getPlanTime(),
                plan.getPlanContent(),
                plan.getImage().getImageId(),
                plan.getImage().getAccessId()
        );
    }

    public PlanDetailOutDto getPlanDetail(UUID planId){
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_PLAN));

        ImageInfo image = modelMapper.map(plan.getImage(), ImageInfo.class);

        return new PlanDetailOutDto(
                plan.getPlanId(),
                plan.getPlanTime(),
                plan.getPlanContent(),
                image,
                plan.isAlarmTf()
        );
    }

    public List<PlanThumbnailOutDto> getPlanPreview(UUID userId) {
        List<Plan> plans = planRepository.findTop3ByUser_UserIdOrderByPlanTimeDesc(userId);
        return plans.stream()
                .map(plan -> {
                    Image image = plan.getImage();
                    return new PlanThumbnailOutDto(
                            plan.isAlarmTf(),
                            plan.getPlanTime(),
                            image.getImageId(),
                            image.getAccessId(),
                            image.isStar()
                    );
                })
                .collect(Collectors.toList());
    }

    public Page<ImageThumbnailOutDto> getStarredImages(UUID userId, Pageable pageable) {
        Page<Image> images = imageRepository.findByUser_UserIdAndStarTrueOrderByImageTimeDesc(userId, pageable);

        return images.map(image -> modelMapper.map(image, ImageThumbnailOutDto.class));
    }

    public Page<PlanThumbnailOutDto> getPlanList(UUID userId, Pageable pageable){
        Page<Plan> plans = planRepository.findByUser_UserIdOrderByPlanTimeDesc(userId, pageable);

        return plans.map(plan -> {
            Image image = plan.getImage();
            return new PlanThumbnailOutDto(
                    plan.isAlarmTf(),
                    plan.getPlanTime(),
                    image.getImageId(),
                    image.getAccessId(),
                    image.isStar()
            );
        });

    }

    public void deletePlan(UUID planId){
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_PLAN));

        planRepository.delete(plan);
    }

    public void updateAlarmTF(PlanAlarmToggleInDto inDto){
        Plan plan = planRepository.findById(inDto.getPlanId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_PLAN));

        plan.setAlarmTf(inDto.isAlarmTf());
        planRepository.save(plan);
    }


}
