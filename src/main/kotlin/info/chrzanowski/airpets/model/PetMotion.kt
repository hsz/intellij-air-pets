package info.chrzanowski.airpets.model

import kotlin.math.abs

class PetMotion(id: String) {
    var position: Double = Math.floorMod(id.hashCode(), 1000) / 1000.0
        private set
    var facingRight: Boolean = id.hashCode() and 1 == 0
        private set
    var elapsedSeconds: Double = 0.0
        private set

    fun advance(seconds: Double, laneWidth: Double, state: PetState) {
        val delta = seconds.coerceIn(0.0, 0.1)
        elapsedSeconds += delta
        if (laneWidth <= 0.0 || (state != PetState.WORKING && state != PetState.REVIEW)) return
        val speed = if (state == PetState.WORKING) 24.0 else 10.0
        position += (if (facingRight) 1 else -1) * speed * delta / laneWidth
        if (position >= 1.0 || position <= 0.0) {
            position = position.coerceIn(0.0, 1.0)
            facingRight = !facingRight
        }
    }

    fun bounce(state: PetState): Int = when (state) {
        PetState.WAITING, PetState.ERROR -> if ((elapsedSeconds * 3).toInt() % 4 == 0) 6 else 0
        PetState.DONE -> (abs(kotlin.math.sin(elapsedSeconds * 3)) * 8).toInt()
        else -> 0
    }
}