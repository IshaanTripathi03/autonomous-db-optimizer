package com.ishaan.dboptimizer.diagnosis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TableNameExtractor {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|INTO|UPDATE|TABLE)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\"?([a-zA-Z_][a-zA-Z0-9_]*)\"?"
    );

    public static String extract(String sql) {
        if (sql == null) {
            return null;
        }
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}