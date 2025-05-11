package org.dslul.openboard.inputmethod.backup

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.dslul.openboard.inputmethod.backup.model.GalleryImage
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 서버로 이미지 업로드를 수행하는 유틸 클래스
 */
object ImageUploader {
    private const val TAG = "ImageUploader"
    // private const val SERVER_URL = "https://k12e201.p.ssafy.io/rag/upload-image/"
    private const val SERVER_URL = "http://k12e201.p.ssafy.io:8090/rag/upload-image/"

    /**
     * 이미지 리스트를 서버에 업로드
     * @param context Context
     * @param images 업로드할 이미지 목록
     * @param userId 사용자 ID
     * @param accessToken JWT 토큰
     * @param onSuccess 성공 시 콜백 (image.contentId 전달)
     * @param onFailure 실패 시 콜백 (filename, throwable 전달)
     */
    fun uploadImages(
        context: Context,
        images: List<GalleryImage>,
        userId: String,
        accessToken: String,
        onSuccess: (String) -> Unit = {},
        onFailure: (String, Throwable) -> Unit = { _, _ -> }
    ) {
        // HTTP 요청을 처리할 OkHttpClient 인스턴스를 생성
        val client = OkHttpClient()

        // 넘겨받은 이미지 리스트를 순회하며 하나씩 업로드
        images.forEach { image ->
            try {
                // MediaStore URI로부터 InputStream 열기
                val inputStream: InputStream? = context.contentResolver.openInputStream(image.uri)
                if (inputStream == null) {
                    // HTTP 요청을 처리할 OkHttpClient 인스턴스를 생성
                    Log.w(TAG, "InputStream is null for ${image.uri}")
                    return@forEach
                }

                // 이미지 바이트로 읽기 + 적절한 MIME 타입 지정
                val imageBytes = inputStream.readBytes()
                val requestBody = imageBytes.toRequestBody(image.mimeType.toMediaTypeOrNull())

                // timestamp를 yyyy:MM:dd HH:mm:ss 형식으로 변환
                val formattedTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    .format(Date(image.timestamp))

                // Multipart FormData 생성
                val formData = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("user_id", userId)
                    .addFormDataPart("access_id", image.contentId)
                    .addFormDataPart("image_time", formattedTime)
                    .addFormDataPart("file", image.filename, requestBody)
                    .build()


                // HTTP 요청 생성
                val request = Request.Builder()
                    .url(SERVER_URL)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(formData)
                    .build()

                // 비동기 업로드 요청 실행
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Upload failed: ${image.filename}", e)
                        onFailure(image.filename, e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.i(TAG, "✅ Uploaded: ${image.filename}")
                            onSuccess(image.contentId)
                        } else {
                            Log.w(TAG, "⚠️ Upload failed: ${response.code} - ${image.filename}")
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception during upload: ${image.filename}", e)
                onFailure(image.filename, e)
            }
        }
    }
}