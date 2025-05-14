// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/settings/MessageAdapter.java
package org.dslul.openboard.inputmethod.latin.search;

import android.content.ContentUris;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_BOT  = 1;

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
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(msg.getTimestamp());
            ((UserViewHolder) holder).tvMessage
                    .setText(msg.getText() + "\n" + time);
        } else if (holder instanceof BotViewHolder) {
            ((BotViewHolder) holder).bind(msg);
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
        private final Button btnToggle;
        private final GridLayout glImages;
        private final Markwon markwon;

        BotViewHolder(View v) {
            super(v);
            tvMessage   = v.findViewById(R.id.tvBotMessage);
            btnToggle   = v.findViewById(R.id.btnToggleImages);
            glImages    = v.findViewById(R.id.glBotImages);

            markwon = Markwon.create(v.getContext());

            // 링크 자동 인식·클릭 활성화
            tvMessage.setAutoLinkMask(Linkify.WEB_URLS);
            tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
        }

        void bind(Message m) {
            // 1) 본문과 타임스탬프 준비
            String body = m.getAnswer() != null ? m.getAnswer() : m.getText();
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(m.getTimestamp());

            // 2) 마크다운 + 타임스탬프 결합
            //    빈 줄 하나를 두고 Plain Text 형태로 타임스탬프를 붙입니다.
            String markdown = body + "\n\n" + time;

            // 3) Markwon 으로 렌더링
            markwon.setMarkdown(tvMessage, markdown);

            // 4) 버튼 텍스트 & 그리드 가시성 초기화
            if (m.isImagesVisible()) {
                btnToggle.setText("사진 숨기기");
                glImages.setVisibility(View.VISIBLE);
            } else {
                btnToggle.setText("사진 보기");
                glImages.setVisibility(View.GONE);
            }

            // 5) 그리드에 썸네일이 한 번만 채워지도록
            if (glImages.getChildCount() == 0) {
                List<Uri> uris = new ArrayList<>();
                addUrisFromPhotos(m.getPhotoResults(), uris);
                addUrisFromInfos (m.getInfoResults(),  uris);
                addUrisFromChatItems (m.getChatItems(),  uris);

                final int size = dpToPx(120, glImages);
                for (Uri u : uris) {
                    long mediaId = ContentUris.parseId(u);
                    Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
                            glImages.getContext().getContentResolver(),
                            mediaId,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                    );

                    ImageView iv = new ImageView(glImages.getContext());
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = size;
                    lp.height = size;
                    lp.setMargins(0, 0, dpToPx(4, glImages), dpToPx(4, glImages));
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(thumb);
                    glImages.addView(iv);
                }
            }

            // 6) 토글 버튼 클릭 시 보여주기/숨기기
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
                } catch (NumberFormatException ignored) {}
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
                } catch (NumberFormatException ignored) {}
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
                } catch (NumberFormatException ignored) {}
            }
        }

        private int dpToPx(int dp, View v) {
            return Math.round(dp * v.getResources().getDisplayMetrics().density);
        }
    }
}
