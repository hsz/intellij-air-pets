package info.chrzanowski.airpets.ui

import info.chrzanowski.airpets.model.PetKind
import info.chrzanowski.airpets.model.PetState
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties

class PetSpritesTest {
    @Test
    fun `all bundled animations decode and loop`() {
        for (kind in PetKind.entries) {
            val animations = if (kind.pixelArt) listOf(
                PetAnimation.IDLE,
                PetAnimation.WALK,
                PetAnimation.RUN,
            ) else PetAnimation.entries
            for (animation in animations) {
                val resource = "/pets/${kind.resourceName}/${animation.fileName}.gif"
                val stream =
                    requireNotNull(javaClass.getResourceAsStream(resource)) { "Missing pet animation: $resource" }
                val clip = stream.use(GifClip::read)
                assertTrue("$resource is animated", clip.frames.size > 1)
                assertTrue(clip.frames.all { it.width == clip.frames.first().width && it.height == clip.frames.first().height })
                assertSame(clip.frames.first(), clip.frame(0.0))
                assertSame(clip.frames.first(), clip.frame(-1.0))
                assertTrue(clip.frame(60.0) in clip.frames)
                assertTrue(
                    clip.frames.any { image ->
                        (0 until image.width).any { x ->
                            (0 until image.height).any { y ->
                                image.getRGB(
                                    x,
                                    y,
                                ).ushr(24) > 0
                            }
                        }
                    },
                )
            }
        }
    }

    @Test
    fun `cat and dog have complete transparent pixel art loops`() {
        val kinds = PetKind.entries.filter { it.resourceName in setOf("cat", "dog") }
        assertEquals(setOf("cat", "dog"), kinds.map { it.resourceName }.toSet())
        val durations = mapOf(
            PetAnimation.IDLE to 1.44,
            PetAnimation.WALK to 1.08,
            PetAnimation.RUN to 0.72,
            PetAnimation.WAITING to 1.44,
            PetAnimation.DONE to 1.08,
        )
        for (kind in kinds) {
            assertTrue(kind.pixelArt)
            for ((animation, duration) in durations) {
                val resource = "/pets/${kind.resourceName}/${animation.fileName}.gif"
                val clip = requireNotNull(javaClass.getResourceAsStream(resource)).use(GifClip::read)
                assertEquals("$resource frame count", 12, clip.frames.size)
                assertSame("$resource loops at its authored duration", clip.frames.first(), clip.frame(duration))
                val first = clip.frames.first().getRGB(0, 0, 64, 64, null, 0, 64)
                assertTrue("$resource must animate", clip.frames.drop(1).any {
                    !first.contentEquals(it.getRGB(0, 0, 64, 64, null, 0, 64))
                })
                for ((index, image) in clip.frames.withIndex()) {
                    assertEquals(64, image.width)
                    assertEquals(64, image.height)
                    val pixels = image.getRGB(0, 0, 64, 64, null, 0, 64)
                    val opaque = pixels.count { it.ushr(24) > 0 }
                    assertTrue("$resource frame $index must contain a pet, not a background", opaque in 205..3276)
                    for (y in 0 until 64) {
                        for (x in 0 until 64) {
                            if (x < 3 || y < 3 || x >= 61 || y >= 61) {
                                assertEquals("$resource frame $index margin ($x, $y)", 0, image.getRGB(x, y).ushr(24))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `mascot clips are transparent bounded and animate without background trails`() {
        val references = Properties().apply {
            requireNotNull(PetSpritesTest::class.java.getResourceAsStream("/pets/mascot-frames.properties")).use(::load)
        }
        for (kind in PetKind.entries.filterNot { it.pixelArt }) {
            val name = kind.resourceName
            for (animation in PetAnimation.entries) {
                val resource = "/pets/$name/${animation.fileName}.gif"
                val clip = requireNotNull(javaClass.getResourceAsStream(resource)).use(GifClip::read)
                for ((index, image) in clip.frames.withIndex()) {
                    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                    val opaque = pixels.count { it.ushr(24) > 0 }
                    assertTrue("$resource frame $index must contain the mascot", opaque > pixels.size / 20)
                    assertTrue("$resource frame $index must not paint its artboard", opaque < pixels.size * 0.8)
                    assertEquals(
                        "$resource frame $index must have a transparent corner",
                        0,
                        image.getRGB(0, 0).ushr(24),
                    )
                    val digest = MessageDigest.getInstance("SHA-256")
                    val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
                    pixels.forEach(bytes::putInt)
                    assertEquals(
                        "$resource frame $index must exactly match the independently composited artwork",
                        requireNotNull(references.getProperty("$name.${animation.fileName}.$index")),
                        HexFormat.of().formatHex(digest.digest(bytes.array())),
                    )
                }
                assertTrue(
                    "$resource must have different animation frames",
                    clip.frames.drop(1).any {
                        !it.getRGB(0, 0, it.width, it.height, null, 0, it.width).contentEquals(
                            clip.frames.first().getRGB(0, 0, it.width, it.height, null, 0, it.width),
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `clippy clips retain transparent margins and original loop timing`() {
        val durations = mapOf(
            PetAnimation.IDLE to 2.4,
            PetAnimation.WALK to 1.68,
            PetAnimation.RUN to 0.96,
            PetAnimation.WAITING to 2.4,
            PetAnimation.DONE to 1.68,
        )
        for ((animation, duration) in durations) {
            val resource = "/pets/clippy/${animation.fileName}.gif"
            val clip = requireNotNull(javaClass.getResourceAsStream(resource)).use(GifClip::read)
            assertEquals("$resource frame count", 24, clip.frames.size)
            assertSame("$resource loops at its authored duration", clip.frames.first(), clip.frame(duration))
            for ((index, image) in clip.frames.withIndex()) {
                assertEquals(128, image.width)
                assertEquals(128, image.height)
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        if (x < 3 || y < 3 || x >= image.width - 3 || y >= image.height - 3) {
                            assertEquals("$resource frame $index margin ($x, $y)", 0, image.getRGB(x, y).ushr(24))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `mascots use supplied state clips while original pets retain idle fallbacks`() {
        val sprites = PetSprites.load()
        for (kind in PetKind.entries) {
            val idleFallback = kind.resourceName in setOf("snake", "rubber-duck")
            val expected = mapOf(
                PetState.WORKING to "run",
                PetState.REVIEW to "walk",
                PetState.WAITING to if (idleFallback) "idle" else "waiting",
                PetState.DONE to if (idleFallback) "idle" else "done",
                PetState.ERROR to "idle",
                PetState.RESTING to "idle",
            )
            for ((state, animation) in expected) {
                val clip =
                    requireNotNull(javaClass.getResourceAsStream("/pets/${kind.resourceName}/$animation.gif")).use(
                        GifClip::read,
                    )
                for (seconds in listOf(0.0, 0.3, 1.2)) {
                    val actual = sprites.frame(kind, state, seconds)
                    val frame = clip.frame(if (state == PetState.RESTING) 0.0 else seconds)
                    assertArrayEquals(
                        "${kind.resourceName} $state at $seconds",
                        frame.getRGB(0, 0, frame.width, frame.height, null, 0, frame.width),
                        actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width),
                    )
                }
            }
        }
    }

    @Test
    fun `each state has a valid sprite`() {
        val sprites = PetSprites.load()
        PetKind.entries.forEach { kind ->
            PetState.entries.forEach { state ->
                assertTrue(sprites.frame(kind, state, 1.0).width > 0)
            }
        }
    }
}