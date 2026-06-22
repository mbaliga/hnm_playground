package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Seconds

/**
 * Samples a [ParameterCurve] at an arbitrary time. Curves are the "live knobs, over time": they
 * animate intensity/sharpness/gain/pitch/cutoff. Outside the point range the value holds at the
 * nearest endpoint (clamped), matching Core Haptics / AHAP control-point behaviour.
 */
class CurveSampler(curve: ParameterCurve) {
    private val points: List<ControlPoint> = curve.points.sortedBy { it.time }
    private val interp: Interpolation = curve.interpolation

    fun valueAt(time: Seconds): Double {
        if (points.isEmpty()) return 0.0
        if (time <= points.first().time) return points.first().value
        if (time >= points.last().time) return points.last().value

        // Find the segment [a, b] containing `time`.
        var hi = points.indexOfFirst { it.time >= time }
        if (hi <= 0) return points.first().value
        val lo = hi - 1
        val a = points[lo]
        val b = points[hi]
        val span = b.time - a.time
        if (span <= 0.0) return b.value
        val t = (time - a.time) / span

        return when (interp) {
            Interpolation.STEP -> a.value
            Interpolation.LINEAR -> a.value + (b.value - a.value) * t
            Interpolation.SMOOTH -> {
                // Catmull-Rom through neighbouring points for a smooth (cubic) curve.
                val p0 = points[maxOf(0, lo - 1)].value
                val p1 = a.value
                val p2 = b.value
                val p3 = points[minOf(points.size - 1, hi + 1)].value
                catmullRom(p0, p1, p2, p3, t)
            }
        }
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2 * p1) +
                (-p0 + p2) * t +
                (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                (-p0 + 3 * p1 - 3 * p2 + p3) * t3
            )
    }

    companion object {
        /** Convenience: sample the first curve matching [curve]'s parameter, or return [default]. */
        fun sampleOrDefault(curve: ParameterCurve?, time: Seconds, default: Double): Double =
            if (curve == null) default else CurveSampler(curve).valueAt(time)
    }
}
