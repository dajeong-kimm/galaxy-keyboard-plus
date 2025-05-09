package com.backend.plan.presentation.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanAlarmToggleRequest {
    private boolean alarmTf;
}
