package com.backend.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


@Configuration
public class FirebaseConfig {
    @Value("${firebase.credentials.json-base64}")
    private String firebaseCredentialsBase64;

    @Bean
    public FirebaseApp firebaseApp() {
        try {

            if (firebaseCredentialsBase64 == null || firebaseCredentialsBase64.isBlank()) {
                throw new IllegalStateException("서비스 계정 키를 찾을 수 없습니다.");
            }
            byte[] decodedBytes = Base64.getDecoder().decode(firebaseCredentialsBase64);
            ByteArrayInputStream serviceAccount = new ByteArrayInputStream(decodedBytes);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("FirebaseApp이 성공적으로 초기화되었습니다.");
                return FirebaseApp.initializeApp(options);
            } else {
                System.out.println("FirebaseApp이 이미 초기화되어 있습니다.");
                return FirebaseApp.getInstance();
            }
        } catch (IOException e) {
            System.out.println("서비스 계정 키 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage());
            throw new RuntimeException("Firebase 초기화에 실패했습니다.", e);
        }
    }
}
