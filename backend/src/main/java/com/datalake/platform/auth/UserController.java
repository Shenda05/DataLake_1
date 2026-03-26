package com.datalake.platform.auth;

import com.datalake.platform.common.security.SecurityUtils;
import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(authService.listUsers(), request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> create(@Valid @RequestBody SaveUserRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            authService.createUser(
                new AuthService.SaveUserCommand(body.username(), body.password(), body.roleId(), body.status()),
                SecurityUtils.currentUser().userId()
            ),
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString()
        );
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> update(@PathVariable Long userId, @Valid @RequestBody SaveUserRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            authService.updateUser(
                userId,
                new AuthService.SaveUserCommand(body.username(), body.password(), body.roleId(), body.status()),
                SecurityUtils.currentUser().userId()
            ),
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString()
        );
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long userId, HttpServletRequest request) {
        authService.deleteUser(userId, SecurityUtils.currentUser().userId());
        return ApiResponse.success(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString());
    }

    public record SaveUserRequest(
        @NotBlank(message = "username 不能为空") String username,
        String password,
        @NotNull(message = "roleId 不能为空") Long roleId,
        String status
    ) {
    }
}
