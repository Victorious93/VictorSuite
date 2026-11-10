package com.termux.victor.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ShellQuoteTest {

    @Test
    public void quotesPlainValue() {
        assertEquals("'root'", ShellQuote.quote("root"));
        assertEquals("''", ShellQuote.quote(""));
    }

    @Test
    public void escapesSingleQuotes() {
        assertEquals("'it'\\''s'", ShellQuote.quote("it's"));
        assertEquals("''\\'''\\'''", ShellQuote.quote("''"));
    }

    @Test
    public void metacharactersStayLiteral() {
        assertEquals("'$(rm -rf ~); `x` && | > \"'", ShellQuote.quote("$(rm -rf ~); `x` && | > \""));
    }

    @Test
    public void rejectsControlCharacters() {
        for (String bad : new String[]{"a\nb", "a\rb", "a\u0000b", "a\u007fb", "a\tb"}) {
            try {
                ShellQuote.quote(bad);
                fail("expected rejection");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNull() {
        ShellQuote.quote(null);
    }
}
