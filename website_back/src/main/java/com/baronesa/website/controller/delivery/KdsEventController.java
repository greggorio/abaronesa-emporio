package com.baronesa.website.controller.delivery;

import com.baronesa.website.service.delivery.KdsEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class KdsEventController {

    private final KdsEventService kdsEventService;

    @GetMapping("/kds")
    public SseEmitter subscribe() {
        return kdsEventService.connect();
    }
}
