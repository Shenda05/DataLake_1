package com.datalake.platform.auth;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final AuthService authService;

    public RoleController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(authService.listRoles(), request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString());
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ACTION_role.manage')")
    public ApiResponse<?> update(@PathVariable Long roleId, @Valid @RequestBody SaveRoleRequest body, HttpServletRequest request) {
        return ApiResponse.success(
            authService.updateRole(roleId, new AuthService.SaveRoleCommand(body.roleDesc(), body.menus(), body.actions())),
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString()
        );
    }

    public record SaveRoleRequest(
        String roleDesc,
        @NotEmpty(message = "menus 不能为空") List<@NotBlank(message = "menus 不能包含空值") String> menus,
        List<@NotBlank(message = "actions 不能包含空值") String> actions
    ) {
    }
}
