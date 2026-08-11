package com.aitrainercrm.platform.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal RFC 4180 CSV builder, shared by every {@code :EXPORT}-gated
 * endpoint in the platform (first used by {@code CampaignController} and
 * {@code KnowledgeArticleController} - see those classes' javadoc for why
 * EXPORT had no implementation anywhere in the codebase before this). Not a
 * general-purpose CSV library - just enough quoting/escaping to produce a
 * file Excel/Sheets/Numbers open correctly, without pulling in a dependency
 * for something this small.
 */
public final class CsvWriter {

    private final StringBuilder buffer = new StringBuilder();

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                buffer.append(',');
            }
            buffer.append(escape(cells[i]));
        }
        buffer.append("\r\n");
        return this;
    }

    public CsvWriter row(List<?> cells) {
        return row(cells.toArray());
    }

    /** UTF-8 bytes, prefixed with a BOM so Excel on Windows doesn't mis-detect the encoding and mangle non-ASCII characters. */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        out.writeBytes(buffer.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** A field needs quoting if it contains a comma, a quote, or a line break; embedded quotes double up, per RFC 4180. */
    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
