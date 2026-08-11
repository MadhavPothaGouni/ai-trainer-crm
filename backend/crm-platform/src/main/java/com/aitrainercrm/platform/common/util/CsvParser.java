package com.aitrainercrm.platform.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader, the counterpart to {@link CsvWriter} - first used by
 * {@code ImportExportService} (see its javadoc for why {@code IMPORT} had no implementation
 * anywhere in the codebase before this, exactly the gap {@link CsvWriter} closed for
 * {@code EXPORT}). Not a general-purpose CSV library: handles quoted fields, embedded commas,
 * embedded newlines inside quotes, and doubled-quote escaping, because that's what a file Excel/
 * Sheets/Numbers produces actually looks like - nothing fancier than that.
 */
public final class CsvParser {

    private CsvParser() {
    }

    /**
     * Parses the whole stream into rows of cells. A UTF-8 byte-order-mark at the very start (the
     * same one {@link CsvWriter#toBytes} writes) is stripped if present, so a file this platform
     * exported can be re-imported without the caller having to know that detail. Blank lines
     * (zero cells, or a single empty cell) are skipped rather than treated as a malformed row -
     * trailing blank lines are extremely common in hand-edited or Excel-saved CSVs.
     */
    public static List<List<String>> parse(InputStream input) throws IOException {
        String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }

            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    rowHasContent = true;
                    i++;
                }
                case ',' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rowHasContent = true;
                    i++;
                }
                case '\r' -> i++; // swallow bare CR; the following LF (if any) ends the row below
                case '\n' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    addRowIfNotBlank(rows, currentRow, rowHasContent);
                    currentRow = new ArrayList<>();
                    rowHasContent = false;
                    i++;
                }
                default -> {
                    field.append(c);
                    rowHasContent = true;
                    i++;
                }
            }
        }
        // Last row has no trailing newline in most files - flush whatever's left.
        if (field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            addRowIfNotBlank(rows, currentRow, rowHasContent);
        }

        return rows;
    }

    private static void addRowIfNotBlank(List<List<String>> rows, List<String> row, boolean rowHasContent) {
        boolean effectivelyBlank = !rowHasContent && row.size() == 1 && row.get(0).isEmpty();
        if (!effectivelyBlank) {
            rows.add(row);
        }
    }
}
