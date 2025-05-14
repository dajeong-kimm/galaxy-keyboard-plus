// src/main/java/com/auth/config/OpenApiConfig.java
package com.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Authentication API")
                .version("v1.0.0")
                .description("카카오 로그인, 토큰 재발급, 회원 탈퇴 API 문서")
            );
    }
}
