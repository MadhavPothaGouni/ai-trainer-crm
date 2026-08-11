package com.aitrainercrm.platform.importexport.repository;

import com.aitrainercrm.platform.importexport.entity.ImportRowError;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, UUID> {

    List<ImportRowError> findByImportJobIdOrderByRowNumberAsc(UUID importJobId);
}
