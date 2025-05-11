package org.dslul.openboard.inputmethod.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.dslul.openboard.inputmethod.backup.model.GalleryImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * 기기의 MediaStore에서 모든 이미지(사진) 정보를 불러오는 유틸 클래스
 */
object MediaStoreImageFetcher {

    /**
     * 모든 이미지 정보를 MediaStore에서 불러와 리스트로 반환
     * - 동영상 제외
     * - 최신순 정렬
     */
    fun getAllImages(context: Context): List<GalleryImage> {
        val images = mutableListOf<GalleryImage>() // 결과를 저장할 리스트

        val projection = arrayOf(
            MediaStore.Images.Media._ID,           // 고유 ID (contentUri 생성용)
            MediaStore.Images.Media.DISPLAY_NAME,  // 파일 이름
            MediaStore.Images.Media.DATE_TAKEN,    // 촬영 시간
            MediaStore.Images.Media.MIME_TYPE      // 이미지 타입 (image/jpeg 등)s
        )

        // 최신순 정렬
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        // 외부 저장소의 이미지 URI (사진 전체를 대상으로 함)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cursor = context.contentResolver.query(
            uri,
            projection,
            "${MediaStore.Images.Media.MIME_TYPE} LIKE ?",
            arrayOf("image/%"),
            sortOrder
        )

        // MIME 타입이 image/% 인 항목만 가져오기 (즉, 사진만)
        cursor?.use {
            // 컬럼 인덱스 가져오기
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val timeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            // 커서 순회
            while (it.moveToNext()) {
                val id = it.getLong(idCol)                          // 고유 ID
                val name = it.getString(nameCol)                   // 파일명
                val time = it.getLong(timeCol)                     // 촬영 시간 (timestamp)
                val mime = it.getString(mimeCol)                   // MIME 타입
                val contentUri: Uri = ContentUris.withAppendedId(uri, id) // URI 생성


                // GalleryImage 데이터 클래스에 매핑
                images.add(
                    GalleryImage(
                        uri = contentUri,                             // content://media/external/images/media/{id}
                        filename = name ?: "unknown.jpg",             // 파일명 없을 경우 예외 처리
                        contentId = id.toString(),                    // 고유 ID → 서버 accessId로 사용 가능
                        timestamp = time,                             // 촬영 시각
                        mimeType = mime                               // 예: image/jpeg
                    )
                )
            }
        }

        return images
    }
}