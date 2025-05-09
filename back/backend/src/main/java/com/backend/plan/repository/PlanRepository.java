package com.backend.plan.repository;

import com.backend.plan.domain.entity.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findTop3ByUser_UserIdOrderByPlanTimeDesc(UUID userId);

    Page<Plan> findByUser_UserIdOrderByPlanTimeDesc(UUID userId, Pageable pageable);

    List<Plan> findAllByAlarmTfTrue();
}
