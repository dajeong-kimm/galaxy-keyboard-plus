package org.dslul.openboard.inputmethod.backup

import android.content.Context
import android.util.Log
import androidx.core.content.PermissionChecker
import android.Manifest
import org.dslul.openboard.inputmethod.backup.model.GalleryImage

/**
 * 자동 백업의 전체 흐름을 관리하는 매니저 클래스
 */
object BackupManager {
    private const val TAG = "BackupManager"

    /**
     * 전체 백업 흐름 실행 함수
     */
    fun startBackup(context: Context) {
        // 1. 권한 확인 (API 33 이상은 READ_MEDIA_IMAGES, 그 이하는 READ_EXTERNAL_STORAGE)
        if (!hasReadPermission(context)) {
            Log.w(TAG, "⛔ 저장소 권한이 없습니다. 백업을 건너뜁니다.")
            return
        }

        // 2. 사용자 인증 정보 가져오기
//        val userId = TokenStore.getUserId(context)
//        val accessToken = TokenStore.getAccessToken(context)
//        if (userId.isBlank() || accessToken.isBlank()) {
//            Log.w(TAG, "⛔ 사용자 인증 정보 없음. 백업 중단")
//            return
//        }

        // 3. 이미지 목록 불러오기
        val lastUploadedAt = UploadStateTracker.getLastUploadedAt(context) ?: 0L
        Log.d(TAG, "📌 마지막 업로드된 timestamp: $lastUploadedAt")

        val allImages: List<GalleryImage> = MediaStoreImageFetcher.getAllImages(context)
        Log.d(TAG, "📸 전체 불러온 이미지 수: ${allImages.size}")

        // 4. 마지막 업로드 시간 이후의 이미지만 필터링
        val newImages = allImages
            .filter { it.timestamp >= lastUploadedAt }
            .sortedByDescending { it.timestamp } // 최신순 정렬
            .take(50) /* 최대 50장 제한 */


        if (newImages.isEmpty()) {
            Log.i(TAG, "🟰 업로드할 새로운 이미지가 없습니다.")
            return
        }

        Log.i(TAG, "새 이미지 ${newImages.size}개 업로드 시작")

        // 5. 이미지 업로드
        ImageUploader.uploadImages(
            context = context,
            images = newImages,
            userId = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            accessToken = "",
            onSuccess = { contentId ->
                Log.d(TAG, "✅ 업로드 성공: $contentId")
            },
            onFailure = { filename, error ->
                Log.e(TAG, "❌ 업로드 실패: $filename", error)
            }
        )

        // 6. 가장 마지막 이미지의 timestamp 저장
        val latestTimestamp = newImages.maxOf { it.timestamp }
        UploadStateTracker.setLastUploadedAt(context, latestTimestamp)
    }

    private fun hasReadPermission(context: Context): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES // ✅ 정확한 권한 이름
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
    }
}