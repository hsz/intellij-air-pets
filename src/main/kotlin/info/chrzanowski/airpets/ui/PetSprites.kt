package info.chrzanowski.airpets.ui

import info.chrzanowski.airpets.model.PetKind
import info.chrzanowski.airpets.model.PetState
import org.w3c.dom.Node
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

enum class PetAnimation(val fileName: String, val fallback: PetAnimation? = null) {
    IDLE("idle"), WALK("walk"), RUN("run"), WAITING("waiting", IDLE), DONE("done", IDLE);
}

class PetSprites private constructor(private val clips: Map<Pair<PetKind, PetAnimation>, GifClip>) {
    fun frame(kind: PetKind, state: PetState, seconds: Double): BufferedImage {
        val animation = when (state) {
            PetState.WORKING -> PetAnimation.RUN
            PetState.REVIEW -> PetAnimation.WALK
            PetState.WAITING -> PetAnimation.WAITING
            PetState.DONE -> PetAnimation.DONE
            else -> PetAnimation.IDLE
        }
        return clips.getValue(kind to animation).frame(if (state == PetState.RESTING) 0.0 else seconds)
    }

    companion object {
        fun load(): PetSprites = PetSprites(buildMap {
            for (kind in PetKind.entries) {
                for (animation in PetAnimation.entries) {
                    val path = "/pets/${kind.resourceName}/${animation.fileName}.gif"
                    val stream = PetSprites::class.java.getResourceAsStream(path)
                    val clip = stream?.use(GifClip::read)
                        ?: getValue(kind to requireNotNull(animation.fallback) { "Missing bundled pet: $path" })
                    put(kind to animation, clip)
                }
            }
        })
    }
}

class GifClip private constructor(val frames: List<BufferedImage>, private val delaysMs: List<Int>) {
    private val durationMs = delaysMs.sum()

    fun frame(seconds: Double): BufferedImage {
        var time = ((seconds.coerceAtLeast(0.0) * 1000).toLong() % durationMs).toInt()
        delaysMs.forEachIndexed { index, delay ->
            if (time < delay) return frames[index]
            time -= delay
        }
        return frames.last()
    }

    companion object {
        fun read(stream: InputStream): GifClip {
            ImageIO.createImageInputStream(stream).use { input ->
                val reader = ImageIO.getImageReadersByFormatName("gif").next()
                try {
                    reader.input = input
                    val screen = reader.streamMetadata.getAsTree("javax_imageio_gif_stream_1.0").child("LogicalScreenDescriptor")
                    val width = screen.int("logicalScreenWidth")
                    val height = screen.int("logicalScreenHeight")
                    var canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val frames = mutableListOf<BufferedImage>()
                    val delays = mutableListOf<Int>()
                    repeat(reader.getNumImages(true)) { index ->
                        val metadata = reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0")
                        val descriptor = metadata.child("ImageDescriptor")
                        val control = metadata.child("GraphicControlExtension")
                        val previous = canvas.copy()
                        val left = descriptor.int("imageLeftPosition")
                        val top = descriptor.int("imageTopPosition")
                        canvas.createGraphics().useGraphics { drawImage(reader.read(index), left, top, null) }
                        frames += canvas.copy()
                        delays += (control.int("delayTime") * 10).coerceAtLeast(20)
                        when (control.attributes.getNamedItem("disposalMethod").nodeValue) {
                            "restoreToPrevious" -> canvas = previous
                            "restoreToBackgroundColor" -> canvas.createGraphics().useGraphics {
                                composite = AlphaComposite.Clear
                                fillRect(left, top, descriptor.int("imageWidth"), descriptor.int("imageHeight"))
                            }
                        }
                    }
                    require(frames.isNotEmpty()) { "Pet animation has no frames" }
                    return GifClip(frames, delays)
                } finally {
                    reader.dispose()
                }
            }
        }

        private fun Node.child(name: String): Node = (0 until childNodes.length)
            .map(childNodes::item).first { it.nodeName == name }

        private fun Node.int(name: String): Int = attributes.getNamedItem(name).nodeValue.toInt()

        private fun BufferedImage.copy(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { copy ->
            copy.createGraphics().useGraphics { drawImage(this@copy, 0, 0, null) }
        }

        private inline fun java.awt.Graphics2D.useGraphics(block: java.awt.Graphics2D.() -> Unit) {
            try { block() } finally { dispose() }
        }
    }
}