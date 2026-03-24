package com.meetingmate.app.exception;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long groupId) {
        super("Group not found. groupId=" + groupId);
    }
}