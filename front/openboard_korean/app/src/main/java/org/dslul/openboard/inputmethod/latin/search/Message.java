// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/settings/Message.java
package org.dslul.openboard.inputmethod.latin.search;

import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.util.Date;
import java.util.List;

public class Message {
    public enum Sender {USER, BOT}

    private final Sender sender;
    private final String text;
    private final Date timestamp;
    private final List<String> photoIds;

    private boolean shouldAnimate;


    // 봇 메시지용 생성자: 텍스트와 photoIds
    public Message(Sender sender,
                   String text,
                   Date timestamp,
                   List<String> photoIds,
                   boolean shouldAnimate
    ) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.photoIds = photoIds;
        this.shouldAnimate = shouldAnimate;
    }

    // user 메시지용 생성자 (photoIds=null)
    public Message(Sender sender, String text, Date timestamp) {
        this(sender, text, timestamp, /*photoIds=*/ null, false);
    }

    public Sender getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public List<String> getPhotoIds() {
        return photoIds;
    }

    public boolean shouldAnimate() {
        return shouldAnimate;
    }

    public void setShouldAnimate(boolean v) {
        shouldAnimate = v;
    }
}
