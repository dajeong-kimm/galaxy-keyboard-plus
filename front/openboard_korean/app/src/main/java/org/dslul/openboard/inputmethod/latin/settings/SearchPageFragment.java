package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.ChatApiService;
import org.dslul.openboard.inputmethod.latin.network.MessageRequest;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SearchPageFragment extends Fragment {
    private RecyclerView rvMessages;
    private TextView tvStatus;
    private EditText etMessage;
    private ImageButton btnSend;
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();

    // Retrofit 서비스
    private ChatApiService chatApiService;

    /* 로그 확인용 TAG 정의 */
    private static final String TAG = "SearchPageFragment";
    private static final String API_TAG = "API-DBG";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_page, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        rvMessages = view.findViewById(R.id.rvMessages);
        tvStatus   = view.findViewById(R.id.tvStatus);
        etMessage  = view.findViewById(R.id.etMessage);
        btnSend    = view.findViewById(R.id.btnSend);

        /* ▼ 1) 톱니 아이콘 참조 후 리스너 등록 -------------------- */
        ImageButton btnSettings = view.findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            // 설정 화면으로 진입
            startActivity(new Intent(getActivity(), SettingsActivity.class));
        });
        /* ▲------------------------------------------------------- */

        adapter = new MessageAdapter(messages);
        rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMessages.setAdapter(adapter);

        // Retrofit + OkHttp 로깅 설정
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(120, TimeUnit.SECONDS)   // 연결 타임아웃
                .readTimeout(120, TimeUnit.SECONDS)      // 읽기(응답 대기) 타임아웃
                .writeTimeout(120, TimeUnit.SECONDS)     // 쓰기(요청 바디 전송) 타임아웃
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://k12e201.p.ssafy.io:8090/") // 실제 베이스 URL 로 변경
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        chatApiService = retrofit.create(ChatApiService.class);

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            sendMessage(text);
        });
    }

    private void sendMessage(String text) {
        // 1) 사용자 메시지 추가
        Message userMsg = new Message(
                Message.Sender.USER,
                text,
                new Date()
        );
        messages.add(userMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);

        etMessage.setText("");
        showStatus("응답을 기다리는 중...", true);

        Log.d(TAG, "▶ sendMessage: \"" + text + "\"");

        String userId = "36648ad3-ed4b-4eb0-bcf1-1dc66fa5d258";
        MessageRequest req = new MessageRequest(userId, text);

        /* API 호출 로그 */
        Log.d(API_TAG, "POST /search  userId=" + req.getUserId()
                + "  query=\"" + req.getQuery() + "\"");

        // 2) 네트워크 호출
        chatApiService.search(req.getUserId(), req.getQuery())
                .enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(Call<MessageResponse> call, Response<MessageResponse> resp) {
                        showStatus("", false);

                        Log.d(API_TAG, "HTTP " + resp.code()
                                + " isSuccessful=" + resp.isSuccessful());

                        if (!resp.isSuccessful() || resp.body() == null) {
                            Log.w(API_TAG, "⚠ body null or error");
                            showStatus("서버 응답 오류", true);
                            return;
                        }
                        MessageResponse body = resp.body();

                        /* 응답 원본 JSON을 보고 싶다면 ↓ */
                        Log.d(API_TAG, "body=" + GSON.toJson(body));

                        Log.d(API_TAG, "answer=\"" + body.getAnswer() + "\""
                                + "  photos=" + (body.getPhotoResults()==null?0:body.getPhotoResults().size())
                                + "  infos="  + (body.getInfoResults()==null?0:body.getInfoResults().size()));

                        // getReply() 대신 getAnswer()
                        String botText = body.getAnswer() != null ? body.getAnswer() : "정보를 찾았습니다.";
                        Message botMsg = new Message(
                                Message.Sender.BOT,
                                botText,
                                new Date(),
                                body.getAnswer(),
                                body.getPhotoResults(),
                                body.getInfoResults()
                        );
                        messages.add(botMsg);
                        adapter.notifyItemInserted(messages.size() - 1);
                        rvMessages.scrollToPosition(messages.size() - 1);

//                        displayFullResponse(body);
                    }

                    @Override
                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                        Log.e(API_TAG, "❌ onFailure: " + t.getMessage(), t);
                        showStatus("네트워크 오류: " + t.getMessage(), true);
                    }
                });
    }

    private void showStatus(String msg, boolean visible) {
        tvStatus.setText(msg);
        tvStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
