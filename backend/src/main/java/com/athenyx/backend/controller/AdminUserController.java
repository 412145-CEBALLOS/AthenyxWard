package com.athenyx.backend.controller;

import com.athenyx.backend.dto.*;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<AdminUserListResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.list(query, role, active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getDetail(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResult>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminUserService.search(query, limit));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<AdminUserResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest req,
            Authentication auth) {
        Long actorId = (Long) auth.getPrincipal();
        String actorEmail = (String) auth.getDetails();
        return ResponseEntity.ok(adminUserService.updateRole(actorId, actorEmail, id, req.role()));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<AdminUserResponse> updateActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest req,
            Authentication auth) {
        Long actorId = (Long) auth.getPrincipal();
        String actorEmail = (String) auth.getDetails();
        return ResponseEntity.ok(adminUserService.updateActive(actorId, actorEmail, id, req.active()));
    }

    @PostMapping("/{id}/reset-trial")
    public ResponseEntity<ResetTrialResponse> resetTrial(
            @PathVariable Long id,
            Authentication auth) {
        Long actorId = (Long) auth.getPrincipal();
        String actorEmail = (String) auth.getDetails();
        return ResponseEntity.ok(adminUserService.resetTrial(actorId, actorEmail, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @PathVariable Long id,
            Authentication auth) {
        Long actorId = (Long) auth.getPrincipal();
        String actorEmail = (String) auth.getDetails();
        adminUserService.softDelete(actorId, actorEmail, id);
        return ResponseEntity.noContent().build();
    }
}
