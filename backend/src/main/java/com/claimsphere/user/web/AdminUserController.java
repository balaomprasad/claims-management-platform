package com.claimsphere.user.web;

import com.claimsphere.common.web.PageResponse;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.dto.CreateUserRequest;
import com.claimsphere.user.dto.UserResponse;
import com.claimsphere.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userService.provision(request));
    }

    @GetMapping
    public PageResponse<UserResponse> listUsers(@RequestParam(required = false) Role role,
                                                @PageableDefault(size = 20, sort = "createdAt",
                                                        direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(userService.listUsers(role, pageable));
    }
}
