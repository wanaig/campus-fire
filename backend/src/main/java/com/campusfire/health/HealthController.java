package com.campusfire.health;

import com.campusfire.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("status", "UP");
        details.put("application", applicationName);
        details.put("checkedAt", Instant.now());
        return ApiResponse.success(details);
    }
}

