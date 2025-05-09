package com.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {

		// 환경변수 사용 설정
		// 운영 환경에서는 .env 파일을 무시하고 시스템 환경변수만 사용
		// 개발 환경에서만 .env 로드
		String env = System.getenv("SPRING_PROFILES_ACTIVE");
		if (env == null || env.equals("dev")) {
			Dotenv dotenv = Dotenv.configure()
					.filename(".env")
					.ignoreIfMissing()
					.load();

			dotenv.entries().forEach(entry ->
					// 시스템 속성이 비어있을 경우에만 설정 (운영 환경에서 덮어쓰지 않게)
					System.setProperty(entry.getKey(), System.getProperty(entry.getKey(), entry.getValue()))
			);
		}

		SpringApplication.run(BackendApplication.class, args);
	}

}
