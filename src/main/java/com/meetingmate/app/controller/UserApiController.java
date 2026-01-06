package com.meetingmate.app.controller;

import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.domain.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserApiController {

    @GetMapping("/me")
    public User me(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return userPrincipal.getUser();
    }
}