package com.aitrainercrm.platform.workflow.repository;

import com.aitrainercrm.platform.workflow.entity.WorkflowRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    Page<WorkflowRun> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);
}
