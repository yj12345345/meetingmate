package com.meetingmate.app.auth;

/** Missing, unknown, expired, revoked or reused refresh token: the client must log in again. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
