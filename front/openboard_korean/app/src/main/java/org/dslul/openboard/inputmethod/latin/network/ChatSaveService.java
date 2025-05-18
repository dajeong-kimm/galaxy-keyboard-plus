package org.dslul.openboard.inputmethod.latin.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.ChatSaveRequest;
import org.dslul.openboard.inputmethod.latin.network.ChatStorageApi;
import org.dslul.openboard.inputmethod.latin.network.dto.BaseResponse;
import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class ChatSaveService extends JobIntentService {
    private static final String TAG = "ChatSaveService";
    private static final int JOB_ID = 1001;

    private static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    private static final String EXTRA_SENDER = "EXTRA_SENDER";
    private static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";
    private static final String EXTRA_ITEMS_JSON = "EXTRA_ITEMS_JSON";

    public static void enqueue(
            Context ctx,
            String userId,
            String sender,
            String message,
            String itemsJson
    ) {
        Intent intent = new Intent(ctx, ChatSaveService.class);
        intent.putExtra(EXTRA_USER_ID, userId);
        intent.putExtra(EXTRA_SENDER, sender);
        intent.putExtra(EXTRA_MESSAGE, message);
        if (itemsJson != null) {
            intent.putExtra(EXTRA_ITEMS_JSON, itemsJson);
        }
        enqueueWork(ctx, ChatSaveService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String userId = intent.getStringExtra(EXTRA_USER_ID);
        String sender = intent.getStringExtra(EXTRA_SENDER);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String itemsJson = intent.getStringExtra(EXTRA_ITEMS_JSON);

        // 1) JSON → List<String> (photoIds) 파싱
        List<String> photoIds = new ArrayList<>();
        if (itemsJson != null) {
            Type listType = new TypeToken<ArrayList<String>>() {
            }.getType();
            photoIds = new Gson().fromJson(itemsJson, listType);
        }

        // 2) ID 리스트 → ChatItem 리스트로 변환 (text는 빈 문자열)
        List<ChatItem> items = new ArrayList<>();
        for (String id : photoIds) {
            ChatItem ci = new ChatItem();
            ci.setAccessId(id);
            ci.setText("");
            items.add(ci);
        }

        // 3) ChatSaveRequest에 ChatItem 리스트 전달
        ChatSaveRequest req = new ChatSaveRequest(
                userId,
                sender,
                message,
                items
        );

        ChatStorageApi api = ApiClient.getChatStorageApi();
        try {
            Response<BaseResponse<Void>> resp = api.saveChat(req).execute();
            if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                Log.i(TAG, "✔ chat saved: code=" + resp.code());
            } else {
                Log.w(TAG, "⚠ save failed: code=" + resp.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ exception on saveChat", e);
        }
    }
}
