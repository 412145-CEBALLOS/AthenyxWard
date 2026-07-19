package com.athenyx.backend.util;

import java.util.List;

/**
 * Minimal RFC-4180 CSV encoder.
 *
 * <p>Handles:
 * <ul>
 *   <li>Formula injection: cells starting with {@code =}, {@code +},
 *       {@code -}, {@code @} are escaped by prepending a single quote
 *       ({@code '}) so spreadsheets treat them as text.</li>
 *   <li>Comma, double-quote and CRLF/LF/CR inside a cell: the cell is
 *       wrapped in double-quotes and internal double-quotes are escaped
 *       as {@code ""}.</li>
 * </ul>
 */
public final class CsvWriter {

    private CsvWriter() {}

    public static byte[] writeRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(encode(cells.get(i)));
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || startsWithFormulaChar(value);

        if (needsQuoting) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static boolean startsWithFormulaChar(String value) {
        if (value.isEmpty()) return false;
        char c = value.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@';
    }
}
