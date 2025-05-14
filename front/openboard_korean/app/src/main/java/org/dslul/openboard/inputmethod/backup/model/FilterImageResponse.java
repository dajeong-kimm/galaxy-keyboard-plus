package org.dslul.openboard.inputmethod.backup.model;

public class FilterImageResponse {
    private String httpStatus;
    private boolean isSuccess;
    private String message;
    private int code;
    private FilterImageResult result;

    public String getHttpStatus() {
        return httpStatus;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    public FilterImageResult getResult() {
        return result;
    }
}
