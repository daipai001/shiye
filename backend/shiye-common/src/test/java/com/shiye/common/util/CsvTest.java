package com.shiye.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void escapesCommaQuoteAndNewline() {
        assertEquals("plain", Csv.escape("plain"));
        assertEquals("\"a,b\"", Csv.escape("a,b"));
        assertEquals("\"a\"\"b\"", Csv.escape("a\"b"));
        assertEquals("\"a\nb\"", Csv.escape("a\nb"));
        assertEquals("", Csv.escape(null));
    }
}
