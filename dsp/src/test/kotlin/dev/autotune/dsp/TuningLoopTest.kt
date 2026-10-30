package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Simulated speaker + room + mic so the whole measure -> fit -> apply -> re-measure loop runs headless. */
internal class SimRoom(val fs: Int, private val reflections: List<Pair<Int, Double>> = emptyList()) {
    // (frequency Hz, T60 seconds, peak gain linear)
    val modes = listOf(Triple(62.0, 0.60, 2.8), Triple(118.0, 0.45, 2.2), Triple(3000.0, 0.05, 2.5))
    private val broadShape = EqChain(
        listOf(EqFilter(50.0, 3.0, 0.7), EqFilter(8000.0, -4.0, 0.6), EqFilter(250.0, 2.5, 0.9)),
    )
    val mic = EqFilter(12000.0, 4.0, 0.7) // mic reads +4 dB around 12 kHz; the cal file says so

    val ir: DoubleArray = run {
        val n = fs
        val h = DoubleArray(n)
        val delay = (0.005 * fs).toInt()
        h[delay] = 1.0
        for ((d, a) in reflections) h[delay + d] += a
        val p = EqProcessor(broadShape, fs)
        for (i in h.indices) h[i] = p.process(h[i].toFloat()).toDouble()
        for ((f, t60, g) in modes) {
            val tau = t60 / 6.908
            val a = 2.0 * (g - 1.0) / tau
            for (i in delay until n) {
                val t = (i - delay).toDouble() / fs
                h[i] += a * exp(-t / tau) * cos(2.0 * PI * f * t) / fs * fs / fs
            }
        }
        h
    }

    fun record(played: FloatArray, seed: Long = 1): FloatArray {
        val lead = (0.5 * fs).toInt()
        val total = lead + played.size + (2.0 * fs).toInt()
        val n = Fft.nextPow2(played.size + ir.size)
        val ar = DoubleArray(n); val ai = DoubleArray(n)
        val br = DoubleArray(n); val bi = DoubleArray(n)
        for (i in played.indices) ar[i] = played[i].toDouble()
        for (i in ir.indices) br[i] = ir[i]
        Fft.transform(ar, ai); Fft.transform(br, bi)
        for (k in 0 until n) {
            val r = ar[k] * br[k] - ai[k] * bi[k]
            val i = ar[k] * bi[k] + ai[k] * br[k]
            ar[k] = r; ai[k] = i
        }
        Fft.transform(ar, ai, inverse = true)
        val rec = FloatArray(total)
        val rnd = java.util.Random(seed)
        for (i in 0 until total) {
            val j = i - lead
            rec[i] = ((if (j in 0 until n) ar[j] else 0.0) + rnd.nextGaussian() * 1e-5).toFloat()
        }
        EqProcessor(EqChain(listOf(mic)), fs).process(rec)
        return rec
    }

    fun calibrationFile(): String = buildString {
        appendLine("\"Sens Factor =-1.378dB, SERNO: 7012345\"")
        val g = FreqGrid(20.0, 20000.0, 6)
        for (f in g.freqs) appendLine("%.3f\t%.3f".format(f, mic.magnitudeDb(f, fs.toDouble())))
    }
}

class TuningLoopTest {
    private val fs = 48000

    @Test
    fun calibrationFileParses() {
        val cal = Calibration.parse(SimRoom(fs).calibrationFile())
        assertEquals(-1.378, cal.sensFactorDb!!, 1e-9)
        assertTrue(cal.pointCount > 50)
        assertTrue(cal.gainDbAt(12000.0) > 3.0)
        assertTrue(abs(cal.gainDbAt(200.0)) < 0.3)
    }

    @Test
    fun biquadMatchesItsAnalyticResponse() {
        val f = EqFilter(1000.0, -6.0, 2.0)
        assertEquals(-6.0, f.magnitudeDb(1000.0), 0.01)
        assertTrue(abs(f.magnitudeDb(100.0)) < 0.2)
        // time-domain check: steady 1 kHz tone through the processor is attenuated by ~6 dB
        val p = EqProcessor(EqChain(listOf(f)), fs)
        var peak = 0f
        for (i in 0 until fs) {
            val y = p.process(kotlin.math.sin(2.0 * PI * 1000.0 * i / fs).toFloat())
            if (i > fs / 2) peak = maxOf(peak, abs(y))
        }
        assertEquals(10.0.pow(-6.0 / 20.0), peak.toDouble(), 0.02)
    }

    @Test
    fun loopConvergesAndFindsResonances() {
        val room = SimRoom(fs)
        val cal = Calibration.parse(room.calibrationFile())
        val sweep = Sweep.generate(fs, 6.0)
        val target = Target.HOME_THEATER

        var filters: List<EqFilter> = emptyList()
        var stock: DoubleArray? = null
        var firstMeasurement: Measurement? = null
        var firstRms = 0.0
        var lastRms = 0.0

        // the loop as the app runs it: fit against the stock response and the EQ that was really applied
        for (round in 0 until 5) {
            val played = sweep.copyOf()
            EqProcessor(EqChain.withSafePreamp(filters), fs).process(played)
            val m = Analyzer.analyze(room.record(played, seed = round.toLong()), sweep, fs, cal, gateMs = 500.0)
            lastRms = EqFitter.score(m.magDb, m.grid, target).first
            if (firstMeasurement == null) {
                firstMeasurement = m
                stock = m.magDb
                firstRms = lastRms
            }
            val loop = EqFitter.fitFromStock(stock!!, filters, m.magDb, m.grid, target)
            println("round $round: rms=%.2f filters=%d".format(lastRms, loop.fit.filters.size))
            filters = EqChain.capBoost(loop.fit.filters)
        }

        val finalRms = lastRms
        println("baseline rms=%.2f  final rms=%.2f".format(firstRms, finalRms))
        println(Export.parametricText(EqChain.withSafePreamp(filters)))
        assertTrue(finalRms < firstRms * 0.6, "EQ should cut the error by 40%+ (was $firstRms, now $finalRms)")
        assertTrue(finalRms < 2.5, "final response should be within ~2.5 dB RMS of target, was $finalRms")

        val res = ResonanceDetector.detect(firstMeasurement!!)
        res.forEach {
            println("resonance %.0f Hz  +%.1f dB  Q=%.1f  T60=%s%s ms  [%s]".format(
                it.freqHz, it.prominenceDb, it.q, if (it.t60IsLowerBound) ">" else "~", it.t60Ms?.let { t -> "%.0f".format(t) } ?: "?", it.kind))
        }
        for (expected in listOf(62.0, 3000.0)) {
            assertTrue(res.any { abs(it.freqHz / expected - 1.0) < 0.08 }, "should detect resonance near $expected Hz; found ${res.map { it.freqHz.toInt() }}")
        }
        val lowMode = res.first { abs(it.freqHz / 62.0 - 1.0) < 0.08 }
        val hiMode = res.first { abs(it.freqHz / 3000.0 - 1.0) < 0.08 }
        assertTrue(lowMode.t60Ms!! > hiMode.t60Ms!! * 3, "62 Hz mode must ring much longer than the 3 kHz peak")
    }
}
