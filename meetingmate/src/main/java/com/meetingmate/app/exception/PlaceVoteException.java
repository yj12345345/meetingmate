package com.meetingmate.app.exception;

public class PlaceVoteException extends RuntimeException {

    private final ErrorCode errorCode;

    public PlaceVoteException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
