package com.auth.service;

import com.auth.domain.UserInfo;
import com.auth.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserInfoRepository userRepo;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req)
            throws OAuth2AuthenticationException {

        // ① 카카오 사용자 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate =
                new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(req);

        Map<String, Object> kakaoAccount =
                (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
        String email = (String) kakaoAccount.get("email");

        // ② DB 조회 → 없으면 회원가입
        UserInfo user = userRepo.findByKakaoEmail(email)
                .orElseGet(() -> userRepo.save(
                        UserInfo.builder()
                                .kakaoEmail(email)
                                .build()));

        // ③ Security Context 에 저장할 사용자 객체 리턴
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("userId", user.getId(), "email", email),
                "email");        // nameAttributeKey
    }
}
