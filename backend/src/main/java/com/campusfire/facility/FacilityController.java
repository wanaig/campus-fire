package com.campusfire.facility;

import com.campusfire.auth.AuthService;
import com.campusfire.auth.AuthenticatedUser;
import com.campusfire.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/facilities")
public class FacilityController {
    private final FacilityService service;
    private final AuthService authService;
    public FacilityController(FacilityService service, AuthService authService) { this.service=service; this.authService=authService; }
    private AuthenticatedUser current(Authentication a) { return authService.loadCurrentUser(a.getName()); }
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword) { return ApiResponse.success(service.list(keyword)); }
    @GetMapping("/types") public ApiResponse<?> types() { return ApiResponse.success(service.types()); }
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable long id) { return ApiResponse.success(service.detail(id)); }
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody FacilityDtos.CreateRequest r, Authentication a) { AuthenticatedUser u=current(a); return ApiResponse.success(service.create(r,u.getId(),u.getRoleCode())); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody FacilityDtos.UpdateRequest r,Authentication a) { r.id=id; AuthenticatedUser u=current(a); return ApiResponse.success(service.update(r,u.getId(),u.getRoleCode())); }
    @PostMapping("/{id}/qr/renew") public ApiResponse<?> renewQr(@PathVariable long id,Authentication a) { AuthenticatedUser u=current(a); return ApiResponse.success(service.renewQr(id,u.getId(),u.getRoleCode())); }
    @PostMapping("/{id}/qr/revoke") public ApiResponse<?> revokeQr(@PathVariable long id,Authentication a) { AuthenticatedUser u=current(a); return ApiResponse.success(service.revokeQr(id,u.getId(),u.getRoleCode())); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable long id,Authentication a) { AuthenticatedUser u=current(a); service.delete(id,u.getId(),u.getRoleCode()); return ApiResponse.success(java.util.Collections.singletonMap("deleted",true)); }
}
