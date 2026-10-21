package info.chrzanowski.airpets.ui

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetKind
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class PetMascotGalleryTest {
    @Test
    fun `every pet renders every state at normal and enlarged zoom`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    for ((theme, background) in listOf("dark" to Color(30, 31, 34), "light" to Color(247, 248, 250))) {
                        val rowHeight = JBUI.scale(174)
                        val image = BufferedImage(
                            JBUI.scale(1024),
                            rowHeight * PetKind.entries.size,
                            BufferedImage.TYPE_INT_ARGB,
                        )
                        val graphics = image.createGraphics()
                        try {
                            graphics.color = background
                            graphics.fillRect(0, 0, image.width, image.height)
                            PetKind.entries.forEachIndexed { row, kind ->
                                val panel = PetOverlay(sprites, {}) { _, _, _ -> }
                                try {
                                    val sessions = PetState.entries.map { state ->
                                        val id =
                                            generateSequence(0) { it + 1 }.map { "${kind.resourceName}-${state.name}-$it" }
                                                .first { PetKind.forSession(it) == kind }
                                        PetSession(id, kind.resourceName, state)
                                    }
                                    panel.setSize(image.width, panel.overlayHeight)
                                    panel.update(sessions, demo = true, labels = true, animated = false)
                                    val y = JBUI.scale(84)
                                    val firstX = (0 until panel.width).first { panel.contains(it, y) }
                                    val point = Point(firstX + JBUI.scale(32), y)
                                    panel.dispatchEvent(
                                        MouseEvent(
                                            panel,
                                            MouseEvent.MOUSE_MOVED,
                                            0,
                                            0,
                                            point.x,
                                            point.y,
                                            0,
                                            false,
                                        ),
                                    )
                                    assertTrue(
                                        "${kind.resourceName} must keep the body interactive at $scale",
                                        panel.contains(point.x, point.y),
                                    )
                                    assertFalse("Empty space must pass through at $scale", panel.contains(1, 1))
                                    val petImage = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                                    val petGraphics = petImage.createGraphics()
                                    try {
                                        panel.paint(petGraphics)
                                    } finally {
                                        petGraphics.dispose()
                                    }
                                    for (column in sessions.indices) {
                                        val x1 =
                                            JBUI.scale(12) + column * (panel.width - JBUI.scale(24)) / sessions.size
                                        val x2 =
                                            JBUI.scale(12) + (column + 1) * (panel.width - JBUI.scale(24)) / sessions.size
                                        val pixels = petImage.getRGB(
                                            x1,
                                            JBUI.scale(52),
                                            x2 - x1,
                                            JBUI.scale(64),
                                            null,
                                            0,
                                            x2 - x1,
                                        )
                                        assertTrue(
                                            "${kind.resourceName} column $column must paint its pet at $scale",
                                            pixels.count { it.ushr(24) > 0 } > JBUI.scale(100),
                                        )
                                    }
                                    graphics.drawImage(petImage, 0, row * rowHeight + JBUI.scale(18), null)
                                    graphics.color = if (theme == "dark") Color.WHITE else Color.BLACK
                                    graphics.font = panel.font
                                    graphics.drawString(
                                        kind.resourceName,
                                        JBUI.scale(16),
                                        row * rowHeight + JBUI.scale(16),
                                    )
                                } finally {
                                    panel.dispose()
                                }
                            }
                        } finally {
                            graphics.dispose()
                        }
                        val output = File("build/reports/air-pets/mascots-$theme-${(scale * 100).toInt()}.png")
                        output.parentFile.mkdirs()
                        ImageIO.write(image, "png", output)
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }
}