package com.aitrainercrm.platform.importexport.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One failed CSV row, always attached to a parent {@link ImportJob}. {@code rowNumber} is
 * 1-indexed against the data rows only (the header doesn't count), matching what a user counting
 * rows in a spreadsheet would expect - row 1 is the first record after the header, not the header
 * itself and not a 0-indexed array position.
 */
@Entity
@Table(name = "import_row_errors")
@Getter
@Setter
@NoArgsConstructor
public class ImportRowError extends BaseEntity {

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(nullable = false, length = 500)
    private String message;

    public ImportRowError(UUID importJobId, int rowNumber, String message) {
        this.importJobId = importJobId;
        this.rowNumber = rowNumber;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }
}
