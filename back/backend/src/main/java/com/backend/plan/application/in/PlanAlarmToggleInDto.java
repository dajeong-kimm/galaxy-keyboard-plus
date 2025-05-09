package com.backend.plan.application.in;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanAlarmToggleInDto {
    private UUID planId;
    private boolean alarmTf;
}
