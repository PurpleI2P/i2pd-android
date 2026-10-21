package org.purplei2p.i2pd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemAutostartTest {
    @Test
    fun `firmwares are found by manufacturer or brand`() {
        assertEquals(OemAutostart.XIAOMI, OemAutostart.of("Xiaomi", "Redmi"))
        assertEquals(OemAutostart.XIAOMI, OemAutostart.of("Xiaomi", "POCO"))
        assertEquals(OemAutostart.HONOR, OemAutostart.of("HONOR", "HONOR"))
        assertEquals(OemAutostart.OPPO, OemAutostart.of("OnePlus", "OnePlus"))
        assertEquals(OemAutostart.OPPO, OemAutostart.of("realme", "realme"))
        assertEquals(OemAutostart.VIVO, OemAutostart.of("vivo", "iQOO"))
        assertNull(OemAutostart.of("Fairphone", "Fairphone"))
        assertNull(OemAutostart.of("Google", "google"))
    }
}
