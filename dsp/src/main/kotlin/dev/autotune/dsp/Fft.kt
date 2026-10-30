package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Fft {
    fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** In-place radix-2 FFT. Array lengths must be equal powers of two. Inverse is scaled by 1/n. */
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        require(n > 0 && n and (n - 1) == 0 && im.size == n) { "FFT length must be a power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val wr = cos(ang)
            val wi = sin(ang)
            val half = len / 2
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] += vr
                    im[a] += vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
