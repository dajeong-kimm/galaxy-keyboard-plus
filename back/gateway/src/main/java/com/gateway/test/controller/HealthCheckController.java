package com.gateway.test.controller;

import org.springframework.web.bind.annotation.*;

@RestController
public class HealthCheckController {

    @GetMapping("/ping")
    public String pingGet() {
        return "✅ GET: Gateway is alive!";
    }

    @PostMapping("/ping")
    public String pingPost() {
        return "✅ POST: Gateway received your request!";
    }
}
