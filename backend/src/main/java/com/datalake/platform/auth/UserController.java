package com.datalake.platform.auth;

import com.datalake.platform.common.web.ApiResponse;
import com.datalake.platform.common.web.DemoDataFactory;
import com.datalake.platform.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ApiResponse.success(DemoDataFactory.users(), request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString());
    }
}

