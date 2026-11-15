package com.claimsphere.dashboard.web;

import com.claimsphere.dashboard.dto.DashboardFilter;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse;
import com.claimsphere.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Workload, ageing and liability metrics, restricted to claims managers. */
    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public ManagerDashboardResponse managerDashboard(DashboardFilter filter) {
        return dashboardService.managerDashboard(filter);
    }
}
