package com.backend.global.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("자율 프로젝트 E201 API 문서")
                        .description("E201 팀의 자율 프로젝트 API 명세서입니다.")
                        .version("v1.0.0"));
    }

    // 사용자 관련 API 그룹
    @Bean
    public GroupedOpenApi userGroup() {
        return GroupedOpenApi.builder()
                .group("User") // swagger-ui에서 탭 이름
                .pathsToMatch("/api/v1/users/**")
                .build();
    }

    // 이미지 관련 API 그룹
    @Bean
    public GroupedOpenApi imageGroup() {
        return GroupedOpenApi.builder()
                .group("Image")
                .pathsToMatch("/api/v1/images/**")
                .build();
    }
    // 일정 관련 API 그룹
    @Bean
    public GroupedOpenApi planGroup() {
        return GroupedOpenApi.builder()
                .group("Plan")
                .pathsToMatch("/api/v1/plans/**")
                .build();
    }

    // 알람 관련 API 그룹
    @Bean
    public GroupedOpenApi alarmGroup() {
        return GroupedOpenApi.builder()
                .group("Alarm")
                .pathsToMatch("/api/v1/alarms/**")
                .build();
    }

    //채팅 관련 API 그룹
    @Bean
    public GroupedOpenApi chatGroup() {
        return GroupedOpenApi.builder()
                .group("Chat") // swagger-ui에서 탭 이름
                .pathsToMatch("/api/v1/chats/**")
                .build();
    }
}
