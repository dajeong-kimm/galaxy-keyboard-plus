// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/settings/MessageAdapter.java
package org.dslul.openboard.inputmethod.latin.search;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_BOT = 1;

    private final List<Message> messages;

    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getSender() == Message.Sender.USER
                ? VIEW_TYPE_USER
                : VIEW_TYPE_BOT;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_bot, parent, false);
            return new BotViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvMessage
                    .setText(msg.getText());

        } else if (holder instanceof BotViewHolder) {
            BotViewHolder botHolder = (BotViewHolder) holder;

            // 1) 메시지 텍스트 설정
            botHolder.tvMessage.setText(msg.getText());
            // 2) 텍스트 선택 가능하게
            botHolder.tvMessage.setTextIsSelectable(true);

            // 3) 복사 버튼 보이기/동작 설정
            String body = msg.getAnswer() != null ? msg.getAnswer() : msg.getText();
            if (body != null && !body.trim().isEmpty()) {
                botHolder.btnCopy.setVisibility(View.VISIBLE);
                botHolder.btnCopy.setOnClickListener(v -> {
                    ClipboardManager cm =
                            (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("bot message", msg.getText());
                    cm.setPrimaryClip(clip);
                });
            } else {
                botHolder.btnCopy.setVisibility(View.GONE);
            }

            botHolder.bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;

        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvUserMessage);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final ImageButton btnCopy;
        private final Button btnToggle;
        private final GridLayout glImages;
        private final Markwon markwon;

        BotViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvBotMessage);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnToggle = v.findViewById(R.id.btnToggleImages);
            glImages = v.findViewById(R.id.glBotImages);

            markwon = Markwon.create(v.getContext());

            // 링크 자동 인식·클릭 활성화
            tvMessage.setAutoLinkMask(Linkify.WEB_URLS);
            tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
        }

        void bind(Message m) {
            // 1) 본문 또는 answer 준비
            String body = m.getAnswer() != null ? m.getAnswer() : m.getText();

            // 2) PhotoResult/InfoResult/ChatItem → Uri 변환
            List<Uri> uris = new ArrayList<>();
            addUrisFromPhotos(m.getPhotoResults(), uris);
            addUrisFromInfos(m.getInfoResults(), uris);
            addUrisFromChatItems(m.getChatItems(), uris);

            boolean hasPhotos = !uris.isEmpty();
            boolean hasText = body != null && !body.trim().isEmpty();

            if (m.shouldAnimate()) {
                // ── 애니메이션 분기 ────────────────────────────────
                if (hasText) {
                    // 텍스트 있을 때 타자기 효과
                    tvMessage.setText("");
                    btnToggle.setVisibility(View.GONE);
                    glImages.setVisibility(View.GONE);
                    animateText(body);
                } else if (hasPhotos && !hasText) {
                    // 사진만 있을 때 썸네일 순차 생성
                    tvMessage.setText("");
                    btnToggle.setVisibility(View.GONE);
                    glImages.setVisibility(View.VISIBLE);
                    animateImages(uris);
                }
            } else {
                // 애니메이션 꺼짐 또는 히스토리 로드
                renderStatic(body, uris, m);
            }
        }

        private void fillImages(List<Uri> uris) {
            // 그리드에 뷰가 비어 있다고 가정하고 호출됨
            final int size = dpToPx(120, glImages);
            for (Uri u : uris) {
                addSingleImage(u, size);
            }
        }

        private void renderStatic(String body, List<Uri> uris, Message m) {
            // 마크다운 + 토글 처리
            markwon.setMarkdown(tvMessage, body);
            btnToggle.setVisibility(uris.isEmpty() ? View.GONE : View.VISIBLE);
            if (m.isImagesVisible()) {
                btnToggle.setText("사진 숨기기");
                glImages.setVisibility(View.VISIBLE);
            } else {
                btnToggle.setText("사진 보기");
                glImages.setVisibility(View.GONE);
            }

            // 최초 1회만 이미지 채우기
            if (glImages.getChildCount() == 0) {
                fillImages(uris);
            }

            btnToggle.setOnClickListener(v -> {
                boolean now = !m.isImagesVisible();
                m.setImagesVisible(now);
                if (now) {
                    btnToggle.setText("사진 숨기기");
                    glImages.setVisibility(View.VISIBLE);
                } else {
                    btnToggle.setText("사진 보기");
                    glImages.setVisibility(View.GONE);
                }
            });
        }

        // 1) 타자기 효과
        private void animateText(final String text) {
            Handler h = new Handler(Looper.getMainLooper());
            tvMessage.setText("");
            for (int i = 0; i < text.length(); i++) {
                final int idx = i;
                h.postDelayed(() -> tvMessage.append(String.valueOf(text.charAt(idx))),
                        idx * 5L  // 5ms 간격
                );
            }
        }

        // 2) 이미지 순차 추가
        private void animateImages(List<Uri> uris) {
            glImages.removeAllViews();
            Handler h = new Handler(Looper.getMainLooper());
            final int size = dpToPx(120, glImages);
            for (int i = 0; i < uris.size(); i++) {
                final Uri u = uris.get(i);
                h.postDelayed(() -> addSingleImage(u, size), i * 100L);
            }
        }

        // 3) 단일 썸네일 추가 로직 (원래 fillImages 내부 로직에서 분리)
        private void addSingleImage(Uri u, int size) {
            long mediaId = ContentUris.parseId(u);
            Bitmap thumb = null;
            try {
                thumb = MediaStore.Images.Thumbnails.getThumbnail(
                        glImages.getContext().getContentResolver(),
                        mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null);
            } catch (Exception ignored) {
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(0, 0, dpToPx(4, glImages), dpToPx(4, glImages));

            if (thumb != null) {
                ImageView iv = new ImageView(glImages.getContext());
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(thumb);
                // 기존 클릭·롱클릭 리스너 설정 그대로…
                glImages.addView(iv);
            } else {
                TextView tv = new TextView(glImages.getContext());
                tv.setLayoutParams(lp);
                tv.setText("삭제된 사진입니다.");
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(12);
                tv.setTextColor(Color.parseColor("#9E9E9E"));
                glImages.addView(tv);
            }
        }

        // PhotoResult ID → Uri
        private void addUrisFromPhotos(List<PhotoResult> list, List<Uri> out) {
            if (list == null) return;
            for (PhotoResult r : list) {
                try {
                    long id = Long.parseLong(r.getId());
                    out.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // InfoResult ID → Uri
        private void addUrisFromInfos(List<InfoResult> list, List<Uri> out) {
            if (list == null) return;
            for (InfoResult r : list) {
                try {
                    long id = Long.parseLong(r.getId());
                    out.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // PhotoResult ID → Uri
        private void addUrisFromChatItems(List<ChatItem> list, List<Uri> out) {
            if (list == null) return;
            for (ChatItem r : list) {
                try {
                    long id = Long.parseLong(r.getAccessId());
                    out.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        private int dpToPx(int dp, View v) {
            return Math.round(dp * v.getResources().getDisplayMetrics().density);
        }
    }
}
