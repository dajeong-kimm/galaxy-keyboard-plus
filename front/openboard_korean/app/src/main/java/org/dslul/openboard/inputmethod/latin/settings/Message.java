// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/settings/Message.java
package org.dslul.openboard.inputmethod.latin.settings;

import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;

import java.util.Date;
import java.util.List;

public class Message {
    public enum Sender { USER, BOT }

    private final Sender sender;
    private final String text;
    private final Date timestamp;

    private final String answer;
    private final List<PhotoResult> photoResults;
    private final List<InfoResult> infoResults;

    private boolean imagesVisible = false;

    // 사용자 메시지용 생성자 (기존용)
    public Message(Sender sender, String text, Date timestamp) {
        this(sender, text, timestamp,  null, null, null);
    }

    // 봇 메시지용 생성자 (확장)
    public Message(Sender sender,
                   String text,
                   Date timestamp,
                   String answer,
                   List<PhotoResult> photoResults,
                   List<InfoResult> infoResults) {
        this.sender       = sender;
        this.text         = text;
        this.timestamp    = timestamp;
        this.answer       = answer;
        this.photoResults = photoResults;
        this.infoResults  = infoResults;
    }

    public Sender getSender()          { return sender; }
    public String getText()            { return text; }
    public Date getTimestamp()         { return timestamp; }
    public boolean isImagesVisible() { return imagesVisible; }

    public String getAnswer()          { return answer; }
    public List<PhotoResult> getPhotoResults() { return photoResults; }
    public List<InfoResult> getInfoResults()   { return infoResults; }
    public void setImagesVisible(boolean v) { imagesVisible = v; }
}
