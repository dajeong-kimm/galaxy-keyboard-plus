package com.backend.image.repository;

import com.backend.image.domain.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {
    //즐겨찾기 사진의 무한스크롤 형태
    Page<Image> findByUser_UserIdAndStarTrueOrderByImageTimeDesc(UUID userId, Pageable pageable);

    //기본 사진의 무한스크롤 형태
    Page<Image> findByUser_UserIdAndStarFalseOrderByImageTimeDesc(UUID userId, Pageable pageable);

    //즐겨찾기 프리뷰로 보여줄 즐겨찾기 사진 k개
    List<Image> findTop3ByUser_UserIdAndStarTrueOrderByImageTimeDesc(UUID userId);

    //해당 액세스 아이디로 저장된 이미지가 있는지 확인
    boolean existsByUser_UserIdAndAccessId(UUID userId, String accessId);


}
