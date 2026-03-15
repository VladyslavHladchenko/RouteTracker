package com.example.routetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiValueSanitizerTest {
    @Test
    fun `sanitize api value drops blanks and literal null`() {
        assertNull(null.sanitizeApiValue())
        assertNull("".sanitizeApiValue())
        assertNull("   ".sanitizeApiValue())
        assertNull("null".sanitizeApiValue())
        assertNull(" NULL ".sanitizeApiValue())
        assertEquals("2026-03-15T12:00:00+01:00", " 2026-03-15T12:00:00+01:00 ".sanitizeApiValue())
    }
}
