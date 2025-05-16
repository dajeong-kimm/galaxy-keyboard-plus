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

import androidx.core.content.ContextCompat;
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
            botHolder.btnCopy.setVisibility(View.VISIBLE);
            botHolder.btnCopy.setOnClickListener(v -> {
                ClipboardManager cm =
                        (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("bot message", msg.getText());
                cm.setPrimaryClip(clip);
            });

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
            // 1) 본문과 타임스탬프 준비
            String body = m.getAnswer() != null ? m.getAnswer() : m.getText();

            String markdown = body;

            // 2) Markwon 으로 렌더링
            markwon.setMarkdown(tvMessage, markdown);

            // 3) 버튼 텍스트 & 그리드 가시성 초기화
            if (m.isImagesVisible()) {
                btnToggle.setText("사진 숨기기");
                glImages.setVisibility(View.VISIBLE);
            } else {
                btnToggle.setText("사진 보기");
                glImages.setVisibility(View.GONE);
            }

            // 4) 그리드에 썸네일이 한 번만 채워지도록
            if (glImages.getChildCount() == 0) {
                List<Uri> uris = new ArrayList<>();
                addUrisFromPhotos(m.getPhotoResults(), uris);
                addUrisFromInfos(m.getInfoResults(), uris);
                addUrisFromChatItems(m.getChatItems(), uris);

                final int size = dpToPx(120, glImages);
                for (Uri u : uris) {
                    long mediaId = ContentUris.parseId(u);

                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = size;
                    lp.height = size;
                    lp.setMargins(0, 0, dpToPx(4, glImages), dpToPx(4, glImages));


                    Bitmap thumb = null;
                    try {
                        thumb = MediaStore.Images.Thumbnails.getThumbnail(
                                glImages.getContext().getContentResolver(),
                                mediaId,
                                MediaStore.Images.Thumbnails.MINI_KIND,
                                null
                        );
                    } catch (Exception ignored) {
                        // 실패하면 thumb는 null
                    }

                    if (thumb != null) {
                        ImageView iv = new ImageView(glImages.getContext());
                        iv.setLayoutParams(lp);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iv.setImageBitmap(thumb);

                        /* ── (1) 썸네일 클릭 → 갤러리(뷰어) 열기 ────────────────── */
                        iv.setOnClickListener(v -> {
                            Context ctx = v.getContext();
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(u, "image/*");
                            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                ctx.startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(ctx, "이미지 뷰어를 열 수 없습니다", Toast.LENGTH_SHORT).show();
                            }
                        });

                        /* ── (2) 썸네일 길게 터치 → 클립보드 복사 ───────────────── */
                        iv.setOnLongClickListener(v -> {
                            Context ctx = v.getContext();

                            // 1) 클립보드에 URI 복사
                            ClipboardManager cm =
                                    (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newUri(
                                    ctx.getContentResolver(), "image", u);
                            cm.setPrimaryClip(clip);

                            // 2) 진동 (50ms) 발생
                            Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    );
                                } else {
                                    vibrator.vibrate(50);
                                }
                            }
                            return true;   // 롱클릭 이벤트 소비
                        });
                        glImages.addView(iv);
                    } else {
                        // 썸네일이 없으면 텍스트뷰로 대체
                        TextView tv = new TextView(glImages.getContext());
                        tv.setLayoutParams(lp);
                        tv.setText("삭제된 사진입니다.");
                        tv.setGravity(Gravity.CENTER);
                        tv.setTextSize(12);  // 적절히 조절
                        tv.setTextColor(Color.parseColor("#9E9E9E"));
                        // 배경색·모서리 둥글게 등 원하면 스타일 추가
                        glImages.addView(tv);
                    }

                }
            }

            // 5) 토글 버튼 클릭 시 보여주기/숨기기
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
