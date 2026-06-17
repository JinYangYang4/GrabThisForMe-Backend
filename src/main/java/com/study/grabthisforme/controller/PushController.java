package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.PushService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/stream")
    public SseEmitter stream() {
        return pushService.subscribe(AuthContext.requireUserId());
    }
}
