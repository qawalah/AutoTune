package dev.autotune.dsp

/**
 * Moves an equaliser's band gains to new values in small steps instead of one jump. A sudden change of a few dB on
 * playing music is heard as a click (seen with the phone's EQ switched on, off, or rebuilt when a song started).
 */
object EqRamp {
    /** Frames of one change; with [STEP_MS] between them a change takes about 150 ms. */
    const val STEPS = 10
    const val STEP_MS = 15L

    /**
     * The gains (dB) the bands go through from [from] to [to], linear in dB; the last frame is exactly [to].
     * When the band count differs (31 and 61 bands), the move starts from flat.
     */
    fun frames(from: FloatArray, to: FloatArray, steps: Int = STEPS): List<FloatArray> {
        val start = if (from.size == to.size) from else FloatArray(to.size)
        val n = steps.coerceAtLeast(1)
        return List(n) { k ->
            if (k == n - 1) to.copyOf()
            else FloatArray(to.size) { i -> start[i] + (to[i] - start[i]) * (k + 1) / n }
        }
    }
}
