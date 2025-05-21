// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/settings/MessageAdapter.java
package org.dslul.openboard.inputmethod.latin.search;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.commonmark.node.Node;
import org.dslul.openboard.inputmethod.latin.R;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

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
        private final FrameLayout headerCopyZone;      // 복사 버튼 영역
        private final NestedScrollView scrollText;     // 텍스트 스크롤 영역
        private final TextView tvMessage;
        private final ImageButton btnCopy;
        private final GridLayout glImages;
        private final Markwon markwon;

        BotViewHolder(View v) {
            super(v);
            headerCopyZone = v.findViewById(R.id.headerCopyZone);
            scrollText = v.findViewById(R.id.scrollText);
            tvMessage = v.findViewById(R.id.tvBotMessage);
            btnCopy = v.findViewById(R.id.btnCopy);
            glImages = v.findViewById(R.id.glBotImages);

            markwon = Markwon.builder(v.getContext())
                    .usePlugin(CorePlugin.create())         // 기본 Markdown 파싱
                    .usePlugin(LinkifyPlugin.create())     // plain URL 자동 링크화
                    .build();

            // 링크 자동 인식·클릭 활성화
            tvMessage.setMovementMethod(LinkMovementMethod.getInstance());
        }

        void bind(Message m) {
            // 1) 텍스트 유무에 따라 영역 보이기/숨기기
            if (m.getText() == null || m.getText().trim().isEmpty()) {
                headerCopyZone.setVisibility(View.GONE);
                scrollText.setVisibility(View.GONE);
            } else {
                headerCopyZone.setVisibility(View.VISIBLE);
                scrollText.setVisibility(View.VISIBLE);
            }

            // 2) 텍스트 애니메이션
            if (m.shouldAnimate() && !m.getText().isEmpty()) {
                animateTyping(m.getText());
            } else {
                // 그냥 한번에 출력
                markwon.setMarkdown(tvMessage, m.getText());
            }

            // 1) 사진 애니/즉시 추가 (중복 제거)
            glImages.removeAllViews();
            if (m.getPhotoIds() != null) {
                if (m.shouldAnimate()) {
                    animateImages(m.getPhotoIds());
                } else {
                    showAllImages(m.getPhotoIds());
                }
            }
            // 한 번만 애니메이션
            if (m.shouldAnimate()) {
                m.setShouldAnimate(false);
            }
        }

        //  API에서 넘어온 photo_ids → MediaStore Uri
        private void addUrisFromPhotoIds(List<String> ids, List<Uri> out) {
            for (String idStr : ids) {
                try {
                    long id = Long.parseLong(idStr);
                    out.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        private int dpToPx(int dp, View v) {
            return Math.round(dp * v.getResources().getDisplayMetrics().density);
        }

        // 텍스트 타이핑 효과
        private void animateTyping(String fullText) {
            // 1) Markdown 파싱 → Spanned 얻기
            Node node = markwon.parse(fullText);
            Spanned spanned = markwon.render(node);

            // 2) Spanned → Spannable (SpannableStringBuilder)
            Spannable spannable = new SpannableStringBuilder(spanned);

            // 3) 빈 문자열로 초기화
            tvMessage.setText("");

            Handler h = new Handler(Looper.getMainLooper());
            int delay = 10; // ms 간격

            // 4) Spannable 길이만큼 reveal
            for (int i = 1; i <= spannable.length(); i++) {
                final int idx = i;
                h.postDelayed(() -> {
                    // Spannable 의 일부분을 잘라서 넣으면, span 정보가 유지됩니다.
                    tvMessage.setText(spannable.subSequence(0, idx),
                            TextView.BufferType.SPANNABLE);
                }, delay * i);
            }
        }

        // 이미지 하나씩 순차 추가
        private void animateImages(List<String> photoIds) {
            Handler h = new Handler(Looper.getMainLooper());
            int delayPer = 50; // 한 장당 지연(ms)
            for (int i = 0; i < photoIds.size(); i++) {
                final String id = photoIds.get(i);
                h.postDelayed(() -> {
                    Uri u = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            Long.parseLong(id)
                    );
                    addOneImage(u);
                }, delayPer * i);
            }
        }

        // 즉시 모두 추가
        private void showAllImages(List<String> photoIds) {
            for (String id : photoIds) {
                Uri u = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        Long.parseLong(id));
                addOneImage(u);
            }
        }

        // 단일 이미지 추가 (Glide 포함)
        private void addOneImage(Uri u) {
            int size = dpToPx(80, glImages);
            ImageView iv = new ImageView(glImages.getContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(dpToPx(2, glImages), dpToPx(2, glImages),
                    dpToPx(2, glImages), dpToPx(2, glImages));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

            Glide.with(iv.getContext())
                    .load(u)
                    .override(size, size)
                    .centerCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e,
                                                    Object model,
                                                    Target<Drawable> target,
                                                    boolean isFirstResource) {
                            // 로드 실패 시 iv만 제거 (텍스트 뷰 추가 없음)
                            glImages.removeView(iv);
                            return true;    // true를 반환해서 Glide에 더 이상 처리를 넘기지 않음
                        }

                        @Override
                        public boolean onResourceReady(Drawable res,
                                                       Object model,
                                                       Target<Drawable> target,
                                                       DataSource src,
                                                       boolean first) {
                            return false;
                        }
                    })
                    .into(iv);

            // 클릭/롱클릭 리스너
            iv.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(u, "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    v.getContext().startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                }
            });
            iv.setOnLongClickListener(v -> {
                ClipboardManager cm = (ClipboardManager)
                        v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newUri(
                        v.getContext().getContentResolver(), "image", u));
                Vibrator vib = (Vibrator)
                        v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(50,
                                VibrationEffect.DEFAULT_AMPLITUDE));
                    } else vib.vibrate(50);
                }
                return true;
            });
            glImages.addView(iv);
        }
    }
}
