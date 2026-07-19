package com.athenyx.backend.controller;

import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.config.dto.ConfigCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final ConfigService configService;

    @GetMapping
    public ResponseEntity<List<ConfigCategoryResponse>> publicList() {
        return ResponseEntity.ok(configService.getPublicGrouped());
    }
}
