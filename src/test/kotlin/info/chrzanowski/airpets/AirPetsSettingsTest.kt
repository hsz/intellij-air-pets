package info.chrzanowski.airpets

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.*
import org.junit.Test

class AirPetsSettingsTest {
    @Test
    fun `metrics are opt in and old preferences keep their defaults`() {
        val options = XmlSerializer.deserialize(org.jdom.Element("state"), PetOptions::class.java)
        assertFalse(options.cost)
        assertFalse(options.duration)
        assertTrue(options.labels)
        assertTrue(options.animated)
        assertTrue(options.enabled)
    }

    @Test
    fun `cost and time preferences persist independently`() {
        for (cost in listOf(false, true)) {
            for (duration in listOf(false, true)) {
                val options = PetOptions().apply {
                    this.cost = cost
                    this.duration = duration
                }
                val restored = XmlSerializer.deserialize(XmlSerializer.serialize(options), PetOptions::class.java)
                assertEquals(cost, restored.cost)
                assertEquals(duration, restored.duration)
            }
        }
    }
}