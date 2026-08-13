package com.aitrainercrm.platform.sequence.repository;

import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceStepRepository extends JpaRepository<SequenceStep, UUID> {

    List<SequenceStep> findBySequenceIdOrderByStepOrderAsc(UUID sequenceId);

    void deleteBySequenceId(UUID sequenceId);

    long countBySequenceId(UUID sequenceId);
}
