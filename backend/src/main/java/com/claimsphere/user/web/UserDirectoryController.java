package com.claimsphere.user.web;

import com.claimsphere.user.domain.Role;
import com.claimsphere.user.dto.UserResponse;
import com.claimsphere.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Directory lookups needed by staff workflows (e.g. picking an officer when assigning a claim).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserDirectoryController {

    private static final int MAX_OFFICERS = 200;

    private final UserService userService;

    public UserDirectoryController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/officers")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public List<UserResponse> listOfficers() {
        return userService.listUsers(Role.CLAIMS_OFFICER, PageRequest.of(0, MAX_OFFICERS, Sort.by("fullName")))
                .getContent();
    }
}
