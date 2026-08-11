package com.aitrainercrm.platform.dashboard.repository;

import com.aitrainercrm.platform.dashboard.entity.DashboardWidget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, UUID> {

    List<DashboardWidget> findByDashboardIdOrderByDisplayOrderAsc(UUID dashboardId);

    Optional<DashboardWidget> findByIdAndDashboardId(UUID id, UUID dashboardId);
}
