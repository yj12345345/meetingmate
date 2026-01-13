package com.meetingmate.app.exception;

public class GroupAccessDeniedException extends RuntimeException {
    public GroupAccessDeniedException(Long groupId) {
        super("Group access denied. groupId=" + groupId);
    }
}