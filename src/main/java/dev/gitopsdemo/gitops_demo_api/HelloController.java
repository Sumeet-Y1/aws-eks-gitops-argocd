package dev.gitopsdemo.gitops_demo_api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello from GitOps demo API!",
                "version", "v1",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("status", "up");
    }
}
