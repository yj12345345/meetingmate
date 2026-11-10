package com.meetingmate.app.exception;

/**
 * The token is valid but its user no longer exists (e.g. account deleted while the
 * access token had not expired yet). Treated as an authentication failure (401) so the
 * frontend clears its session, rather than 400 "bad request".
 */
public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException(Long userId) {
        super("Authenticated user not found: " + userId);
    }
}
