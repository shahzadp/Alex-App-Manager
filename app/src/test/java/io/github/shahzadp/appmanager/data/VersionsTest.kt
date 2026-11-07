package io.github.shahzadp.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    @Test
    fun comparesNumericSegments() {
        assertTrue(Versions.compare("1.37", "1.36") > 0)
        assertTrue(Versions.compare("0.5.13.6", "0.5.13.5") > 0)
        assertTrue(Versions.compare("1.0", "1.0.0") == 0)
        assertTrue(Versions.compare("2", "10") < 0)
    }

    @Test
    fun ignoresTagPrefixes() {
        assertEquals(0, Versions.compare("v1.2.3", "1.2.3"))
        assertEquals(0, Versions.compare("V0.6", "0.6"))
    }

    @Test
    fun treatsMissingInstalledVersionAsNewer() {
        assertTrue(Versions.isNewer("1.0", null))
        assertFalse(Versions.isNewer("1.0", "1.0"))
        assertFalse(Versions.isNewer("1.0", "1.1"))
    }
}
