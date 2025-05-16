package org.dslul.openboard.inputmethod.latin.search;

import android.app.Fragment;
import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;

import com.airbnb.lottie.LottieAnimationView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.Chat;
import org.dslul.openboard.inputmethod.latin.network.ChatApiService;
import org.dslul.openboard.inputmethod.latin.network.ChatHistoryResponse;
import org.dslul.openboard.inputmethod.latin.network.ChatPage;
import org.dslul.openboard.inputmethod.latin.network.ChatSaveRequest;
import org.dslul.openboard.inputmethod.latin.network.ChatSaveService;
import org.dslul.openboard.inputmethod.latin.network.ChatStorageApi;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.MessageRequest;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.network.dto.BaseResponse;
import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchPageFragment extends Fragment {
    private RecyclerView rvMessages;
    private ImageButton btnScrollBottom;
    private TextView tvStatus;
    private LottieAnimationView avLoading;
    private EditText etMessage;
    private ImageButton btnSend;
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();

    // Retrofit 서비스
    private ChatApiService chatApiService;

    /* 로그 확인용 TAG 정의 */
    private static final String TAG = "SearchPageFragment";
    private static final String API_TAG = "API-DBG";
    private static final String TAG_UI = "SEARCH-UI";
    private static final String TAG_LOAD = "SEARCH-LOAD";
    private static final String TAG_SEND = "SEARCH-SEND";
    private static final String TAG_SAVE = "SEARCH-SAVE";
    private static final String TAG_SCROLL = "SEARCH-SCROLL";
    private static final String TAG_API = "SEARCH-API";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /* 기존 메시지 저장용 */
    private ChatStorageApi storageApi;
    private boolean loading = false;
    private int page = 0;
    private boolean last = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_page, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(TAG_UI, "onViewCreated()");
        rvMessages = view.findViewById(R.id.rvMessages);
        btnScrollBottom = view.findViewById(R.id.btnScrollBottom);
        tvStatus = view.findViewById(R.id.tvStatus);
        avLoading = view.findViewById(R.id.avLoading);
        etMessage = view.findViewById(R.id.etMessage);
        btnSend = view.findViewById(R.id.btnSend);

        adapter = new MessageAdapter(messages);
        rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMessages.setAdapter(adapter);

        // 스크롤 리스너: 맨 아래 아닐 때만 버튼 보이기
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount() - 1;
                // 맨 아래가 아니면 보이기
                if (lastVisible < total) {
                    btnScrollBottom.setVisibility(View.VISIBLE);
                } else {
                    btnScrollBottom.setVisibility(View.GONE);
                }
            }
        });

        // 버튼 클릭 시 맨 아래로 스크롤
        btnScrollBottom.setOnClickListener(v -> {
            int lastPos = adapter.getItemCount() - 1;
            if (lastPos >= 0) {
                rvMessages.smoothScrollToPosition(lastPos);
            }
        });

        /* ── 스크롤 리스너 등록 ───────────────────────── */
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                int firstPos = lm.findFirstVisibleItemPosition();
                Log.v(TAG_SCROLL, "onScrolled dx=" + dx + " dy=" + dy
                        + " firstVisible=" + firstPos);            // 🔍
                if (firstPos <= 10) {
                    Log.d(TAG_SCROLL, "Top reached → loadPage(" + page + ")"); // 🔍
                    loadPage(page);
                }
            }
        });

        /* Retrofit 서비스들 초기화 */
        chatApiService = ApiClient.getChatApiService();
        storageApi = ApiClient.getChatStorageApi();

        /* 최초 0페이지 불러오기 */
        loadPage(0);

        /* 전송 버튼 리스너 */
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            sendMessage(text);
        });
    }

    private void sendMessage(String text) {
        Log.d(TAG_SEND, "sendMessage() text=\"" + text + "\"");

        // 0) 시작 시점 기록
        final long startTime = System.currentTimeMillis();

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
        showLoading(true);

        Log.d(TAG, "▶ sendMessage: \"" + text + "\"");

        /* 사용자 ID - AuthManager에서 가져오기 ------------- */
        String userId = AuthManager.getInstance(getContext()).getUserId();
        if (userId == null || userId.isEmpty()) {          // 로그인 안 된 경우
            Log.w(TAG, "userId null → 로그인 필요");
            showStatus("로그인 후 이용해 주세요", true);
            return;
        }

        MessageRequest req = new MessageRequest(userId, text);

        /* API 호출 로그 */
        Log.i(TAG_API, "POST /search userId=" + req.getUserId()
                + " query=\"" + req.getQuery() + "\"");

        // 사용자 질문 저장
        saveChat(userMsg);

        // 2) 네트워크 호출
        chatApiService.search(req.getUserId(), req.getQuery())
                .enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(Call<MessageResponse> call, Response<MessageResponse> resp) {
                        // 2-1) 응답까지 걸린 시간 계산
                        long elapsed = System.currentTimeMillis() - startTime;
                        Log.d(TAG_SEND, "응답 수신까지 걸린 시간: " + elapsed + "ms");

                        showLoading(false);

                        Log.d(TAG_API, "HTTP " + resp.code()
                                + " isSuccessful=" + resp.isSuccessful());

                        if (!resp.isSuccessful() || resp.body() == null) {
                            Log.w(TAG_API, "⚠ body null or error");
                            showStatus("서버 응답 오류", true);
                            return;
                        }
                        MessageResponse body = resp.body();
                        String type = body.getType();

                        /* 응답 원본 JSON을 보고 싶다면 ↓ */
                        Log.d(API_TAG, "body=" + GSON.toJson(body));

                        // 1) 메시지 텍스트 준비
                        String displayText = "";
                        if ("info_search".equals(type) || "conversation".equals(type)) {
                            displayText = body.getAnswer();             // answer 보여줌
                        }

                        // 2) 이미지 ID → Uri 변환
                        List<Uri> uris = new ArrayList<>();
                        if ("photo_search".equals(type) || "info_search".equals(type)) {
                            for (String id : body.getPhotoIds()) {
                                try {
                                    long mediaId = Long.parseLong(id);
                                    uris.add(ContentUris.withAppendedId(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            mediaId
                                    ));
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                        // 3) 어댑터에 반영
                        Message botMsg = new Message(
                                Message.Sender.BOT,
                                displayText,
                                new Date(),
                                body.getAnswer(),         // answer 필드 (null 허용)
                                /* photoResults */        convertToPhotoResults(body.getPhotoIds()),
                                /* infoResults */         null,
                                /* chatItems */           null,
                                true
                        );
                        messages.add(botMsg);
                        adapter.notifyItemInserted(messages.size() - 1);
                        rvMessages.scrollToPosition(messages.size() - 1);

                        // 봇 응답 저장
                        saveChat(botMsg);
                    }

                    @Override
                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                        // 2-2) 실패까지 걸린 시간 계산
                        long elapsed = System.currentTimeMillis() - startTime;
                        Log.e(TAG_SEND, "네트워크 오류 (걸린 시간: " + elapsed + "ms): " + t.getMessage(), t);

                        showLoading(false);
                        showStatus("네트워크 오류: " + t.getMessage(), true);
                    }
                });
    }

    private List<PhotoResult> convertToPhotoResults(List<String> ids) {
        List<PhotoResult> out = new ArrayList<>();
        for (String id : ids) {
            PhotoResult pr = new PhotoResult();
            pr.setId(id);
            pr.setText(null);
            out.add(pr);
        }
        return out;
    }

    /**
     * 에러/상태 메시지 표시 (스피너 숨김)
     */
    private void showStatus(String msg, boolean visible) {
        avLoading.setVisibility(View.GONE);
        tvStatus.setText(msg);
        tvStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * 로딩 스피너 표시 (상태 메시지 숨김)
     */
    private void showLoading(boolean loading) {
        tvStatus.setVisibility(View.GONE);
        if (loading) {
            avLoading.setVisibility(View.VISIBLE);
            avLoading.playAnimation();
        } else {
            avLoading.pauseAnimation();
            avLoading.setVisibility(View.GONE);
        }
    }

    private void loadPage(int p) {
        Log.d(TAG_LOAD, "loadPage(" + p + ") start loading=" + loading
                + " last=" + last);

        if (loading || last) return;
        loading = true;

        // ── (1) 현재 보고 있는 첫 번째 아이템 위치와 오프셋을 저장 ─────────
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        int firstVisiblePos = lm.findFirstVisibleItemPosition();
        View firstVisibleView = lm.findViewByPosition(firstVisiblePos);
        int offset = (firstVisibleView == null) ? 0 : firstVisibleView.getTop();

        String userId = AuthManager.getInstance(getContext()).getUserId();
        storageApi.getChats(userId, p).enqueue(new Callback<ChatHistoryResponse>() {
            @Override
            public void onResponse(Call<ChatHistoryResponse> call, Response<ChatHistoryResponse> resp) {

                loading = false;

                Log.d(TAG_LOAD, "← /chat page=" + p + " code=" + resp.code()
                        + " success=" + resp.isSuccessful());

                if (!resp.isSuccessful() || resp.body() == null) {
                    Log.w(TAG_LOAD, "page " + p + " load failed");
                    return;
                }

                ChatPage cp = resp.body().getResult();
                List<Chat> list = cp.getChats();
                int newCount = list.size();

                Log.i(TAG_LOAD, "page " + p + " chats=" + newCount
                        + " totalPages=" + cp.getTotalPages()
                        + " last=" + cp.isLast());

                // ── (2) 새로 불러온 메시지를 리스트 앞에 붙인다 ─────────
                for (int i = 0; i < newCount; i++) {
                    messages.add(0, toMessage(list.get(i)));
                }
                adapter.notifyItemRangeInserted(0, newCount);

                // ── (3) 삽입 전과 동일한 스크롤 위치로 복원 ─────────
                lm.scrollToPositionWithOffset(firstVisiblePos + newCount, offset);

                // ── (4) 다음 페이지 인덱스 갱신 ─────────
                page = cp.getCurrentPage() + 1;
                last = cp.isLast();
            }

            @Override
            public void onFailure(Call<ChatHistoryResponse> call, Throwable t) {
                loading = false;
            }
        });
    }

    private void saveChat(Message m) {
        Log.d(TAG_SAVE, "saveChat() sender=" + m.getSender()
                + " text=\"" + m.getText() + "\"");

        List<ChatItem> items = new ArrayList<>();
        if (m.getSender() == Message.Sender.BOT) {
            // ① PhotoResult
            if (m.getPhotoResults() != null) {
                for (PhotoResult pr : m.getPhotoResults()) {
                    // ChatItem(String accessId, String text)
                    items.add(new ChatItem(pr.getId(), pr.getText()));
                }
            }
            // ② InfoResult
            if (m.getInfoResults() != null) {
                for (InfoResult ir : m.getInfoResults()) {
                    items.add(new ChatItem(ir.getId(), ir.getText()));
                }
            }
        }

        // Gson 으로 JSON 문자열로 변환
        String itemsJson = null;
        if (!items.isEmpty()) {
            itemsJson = new Gson().toJson(items);
        }

        // 서비스에 enqueue 할 때 JSON 문자열 넘기기
        ChatSaveService.enqueue(
                getContext(),
                AuthManager.getInstance(getContext()).getUserId(),
                m.getSender() == Message.Sender.USER ? "user" : "bot",
                m.getText(),
                itemsJson     // List<ChatItem> 대신 JSON 문자열
        );
        Log.d(TAG_SAVE, "→ ChatSaveService enqueued (async)");
    }

    // Chat → Message 변환
    private Message toMessage(Chat c) {
        Date when = Date.from(
                LocalDateTime.parse(c.getChatTime())
                        .atZone(ZoneId.systemDefault()).toInstant());

        if ("user".equals(c.getSender())) {
            return new Message(Message.Sender.USER, c.getMessage(), when,
                    null, null, null, c.getItems(), false);
        } else { // bot
            // accessId → PhotoResult/InfoResult 매핑은 필요 시 변환
            return new Message(Message.Sender.BOT, c.getMessage(), when,
                    c.getMessage(), null, null, c.getItems(), false);
        }
    }
}
