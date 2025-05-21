package org.dslul.openboard.inputmethod.event;

public class HangulCommitEvent {
    public static final int TYPE_SYLLABLE = 0;
    public static final int TYPE_END = 1;

    public final int type;
    public final String text;

    public HangulCommitEvent(int type, String text) {
        this.type = type;
        this.text = text;
    }
}
