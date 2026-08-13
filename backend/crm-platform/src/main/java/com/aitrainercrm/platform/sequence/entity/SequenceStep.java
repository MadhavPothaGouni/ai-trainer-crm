package com.aitrainercrm.platform.sequence.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One touch in a {@link Sequence} - the same "real child row, no permission of its own, managed
 * entirely through the parent's service" shape {@code QuoteLineItem} uses for {@code Quote}. {@link
 * #stepOrder} is the position within the sequence (0-based); {@link #dayOffset} is how many days
 * after enrollment this step is meant to happen, purely informational display data - see V32's
 * migration comment for why there is no scheduler here actually enforcing it.
 */
@Entity
@Table(name = "sequence_steps")
@Getter
@Setter
@NoArgsConstructor
public class SequenceStep extends BaseEntity {

    public enum Type {
        EMAIL, CALL, TASK
    }

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Column(length = 200)
    private String subject;

    @Column(length = 4000)
    private String body;

    public SequenceStep(UUID sequenceId, int stepOrder, Type type, int dayOffset) {
        this.sequenceId = sequenceId;
        this.stepOrder = stepOrder;
        this.type = type;
        this.dayOffset = dayOffset;
    }
}
