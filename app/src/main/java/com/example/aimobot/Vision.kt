package com.example.aimobot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object Vision {
    const val S = 3 // fator de redução usado no reconhecimento de sprites

    class Tpl(val w: Int, val h: Int, val sx: IntArray, val sy: IntArray, val sc: IntArray)
    class Hit(val x: Int, val y: Int, val score: Float)

    private fun isFill(c: Int, blue: Boolean): Boolean {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return if (blue) b > 140 && b > r + 50 && b >= g
        else r > 140 && r > g + 60 && r > b + 60
    }

    /** Fração (0..1) preenchida da barra entre A (começo REAL, mesmo escondido atrás do retrato) e B (fim). */
    fun bar(bmp: Bitmap, ax: Float, ay: Float, bx: Float, by: Float, blue: Boolean): Float {
        val w = bmp.width; val h = bmp.height
        val x1 = (ax * w).toInt().coerceIn(0, w - 1)
        val x2 = (bx * w).toInt().coerceIn(0, w - 1)
        if (x2 - x1 < 10) return 0f
        val yc = (((ay + by) / 2f) * h).toInt()
        val row = IntArray(x2 - x1 + 1)
        val run = (w / 370).coerceAtLeast(4)   // pixels seguidos de preenchimento (ignora pontos soltos do texto)
        var best = 0
        for (o in floatArrayOf(-0.0147f, 0.0147f)) {   // só as bordas da barra, longe do texto "73/73"
            val y = (yc + o * h).toInt().coerceIn(0, h - 1)
            bmp.getPixels(row, 0, row.size, x1, y, row.size, 1)
            var n = 0
            for (i in row.indices) {
                if (isFill(row[i], blue)) { n++; if (n >= run && i > best) best = i } else n = 0
            }
        }
        return (best.toFloat() / (x2 - x1)).coerceIn(0f, 1f)
    }

    /** Média por blocos SxS (mantém sprites pixel-art estáveis). */
    fun down(px: IntArray, w: Int, h: Int): IntArray {
        val dw = w / S; val dh = h / S
        val out = IntArray(dw * dh)
        val n = S * S
        for (y in 0 until dh) for (x in 0 until dw) {
            var r = 0; var g = 0; var b = 0
            for (j in 0 until S) {
                val o = (y * S + j) * w + x * S
                for (i in 0 until S) {
                    val c = px[o + i]
                    r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                }
            }
            out[y * dw + x] = ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return out
    }

    /** Recorte alinhado à grade SxS ao redor do toque. */
    fun crop(bmp: Bitmap, cx: Int, cy: Int): Bitmap {
        val side = ((0.12f * bmp.height) / S).toInt().coerceAtLeast(12)
        val maxX = bmp.width / S - side
        val maxY = bmp.height / S - side
        val gx = (cx / S - side / 2).coerceIn(0, maxX)
        val gy = (cy / S - side / 2).coerceIn(0, maxY)
        return Bitmap.createBitmap(bmp, gx * S, gy * S, side * S, side * S)
    }

    /** Guarda só os pixels que diferem do fundo (mediana da borda), assim o chão atrás do monstro não atrapalha. */
    fun makeTemplate(crop: Bitmap): Tpl? {
        val w = crop.width; val h = crop.height
        val px = IntArray(w * h); crop.getPixels(px, 0, w, 0, 0, w, h)
        val d = down(px, w, h)
        val tw = w / S; val th = h / S
        if (tw < 6 || th < 6) return null
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        fun add(c: Int) { rs.add((c shr 16) and 255); gs.add((c shr 8) and 255); bs.add(c and 255) }
        for (x in 0 until tw) { add(d[x]); add(d[(th - 1) * tw + x]) }
        for (y in 0 until th) { add(d[y * tw]); add(d[y * tw + tw - 1]) }
        rs.sort(); gs.sort(); bs.sort()
        val br = rs[rs.size / 2]; val bg = gs[gs.size / 2]; val bb = bs[bs.size / 2]
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); val cs = ArrayList<Int>()
        var k = 0
        for (y in 0 until th) for (x in 0 until tw) {
            val c = d[y * tw + x]
            val diff = abs(((c shr 16) and 255) - br) + abs(((c shr 8) and 255) - bg) + abs((c and 255) - bb)
            if (diff > 90) { if (k++ % 2 == 0) { xs.add(x); ys.add(y); cs.add(c) } }
        }
        if (xs.size < 15) return null
        return Tpl(tw, th, xs.toIntArray(), ys.toIntArray(), cs.toIntArray())
    }

    /** Procura sprites na área (coords reduzidas). Retorna centros em coordenadas de tela cheia. */
    fun find(dp: IntArray, dw: Int, tpls: List<Tpl>, x0: Int, y0: Int, x1: Int, y1: Int, thr: Float): List<Hit> {
        val raw = ArrayList<Hit>()
        for (t in tpls) {
            val n = t.sc.size
            val limit = (thr * n * 3f).toInt()
            var y = y0
            while (y + t.h <= y1) {
                var x = x0
                while (x + t.w <= x1) {
                    var sum = 0
                    var i = 0
                    while (i < n) {
                        val p = dp[(y + t.sy[i]) * dw + x + t.sx[i]]
                        val q = t.sc[i]
                        sum += abs(((p shr 16) and 255) - ((q shr 16) and 255)) +
                            abs(((p shr 8) and 255) - ((q shr 8) and 255)) +
                            abs((p and 255) - (q and 255))
                        if (sum > limit) break
                        i++
                    }
                    if (i == n) raw.add(Hit(x + t.w / 2, y + t.h / 2, sum / (3f * n)))
                    x += 2
                }
                y += 2
            }
        }
        raw.sortBy { it.score }
        val keep = ArrayList<Hit>()
        for (h in raw.take(400)) {
            if (keep.none { abs(it.x - h.x) < 14 && abs(it.y - h.y) < 14 }) keep.add(h)
        }
        return keep.map { Hit(it.x * S, it.y * S, it.score) }
    }
}
