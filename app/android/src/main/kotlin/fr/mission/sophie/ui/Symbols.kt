package fr.mission.sophie.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color

/**
 * The 8 pendant symbols, same geometry as amulettes-mission-sophie.scad (mm, y up, fits in a radius of ~15).
 * Drawn centered in the current DrawScope.
 */
object Symbols {
    private class B(val cx: Float, val cy: Float, val k: Float) {
        val parts = mutableListOf<Path>()
        fun x(v: Float) = cx + v * k
        fun y(v: Float) = cy - v * k
        fun circle(x0: Float, y0: Float, r: Float) = Path().apply { addOval(Rect(Offset(x(x0), y(y0)), r * k)) }
        fun rect(x0: Float, y0: Float, w: Float, h: Float) = Path().apply {
            addRect(Rect(x(x0 - w / 2), y(y0 + h / 2), x(x0 + w / 2), y(y0 - h / 2)))
        }
        fun poly(vararg pts: Pair<Float, Float>) = Path().apply {
            pts.forEachIndexed { i, (a, b) -> if (i == 0) moveTo(x(a), y(b)) else lineTo(x(a), y(b)) }
            close()
        }
        fun bar(x0: Float, x1: Float, yy: Float, h: Float = 3.2f) = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(x(x0 - h / 2), y(yy + h / 2), x(x1 + h / 2), y(yy - h / 2)),
                androidx.compose.ui.geometry.CornerRadius(h / 2 * k)))
        }
        fun diamond(size: Float) = Path().apply {
            val d = size / 2 * 1.41421f
            moveTo(x(0f), y(d)); lineTo(x(d), y(0f)); lineTo(x(0f), y(-d)); lineTo(x(-d), y(0f)); close()
        }
        fun add(p: Path) { parts += p }
        fun minus(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Difference) }
        fun inter(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Intersect) }

        /** A bar of width [w] from ([x0], [y0]) along the direction [deg] (0 = right, counterclockwise) for [len]. */
        fun seg(x0: Float, y0: Float, deg: Float, len: Float, w: Float): Path {
            val a = Math.toRadians(deg.toDouble())
            val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
            val nx = -sn * w / 2; val ny = c * w / 2
            return poly(
                (x0 + nx) to (y0 + ny), (x0 + c * len + nx) to (y0 + sn * len + ny),
                (x0 + c * len - nx) to (y0 + sn * len - ny), (x0 - nx) to (y0 - ny),
            )
        }

        /** A bar along [deg] between the radii [r0] and [r1] from the center. */
        fun ray(deg: Float, r0: Float, r1: Float, w: Float): Path {
            val a = Math.toRadians(deg.toDouble())
            return seg(kotlin.math.cos(a).toFloat() * r0, kotlin.math.sin(a).toFloat() * r0, deg, r1 - r0, w)
        }
    }

    /** Separate pieces: merging them would lose the holes of the ring and the hollow diamond. */
    private val parsed = HashMap<String, Path>()

    /** Talent symbol generated from the 3D model (a child's drawings), or null. */
    private fun generated(id: String, cx: Float, cy: Float, size: Float): Path? {
        val base = parsed.getOrPut(id) {
            val d = SymbolPaths.paths[id] ?: return null
            androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
        }
        val k = size / 30f
        return Path().apply {
            addPath(base)
            transform(androidx.compose.ui.graphics.Matrix().apply { translate(cx, cy); scale(k, k) })
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
    }

    fun paths(id: String, cx: Float, cy: Float, size: Float): List<Path> {
        generated(id, cx, cy, size)?.let { return listOf(it) }
        val b = B(cx, cy, size / 34f)
        with(b) {
            when (id) {
                // The 8 first symbols are those of amulettes-mission-sophie.scad.
                "telepathe" -> { add(circle(-9f, 0f, 5f)); add(circle(9f, 0f, 5f)); add(rect(0f, 0f, 14f, 2.4f)) }
                "polyglotte" -> { add(circle(-10f, 0f, 4f)); add(rect(0f, 0f, 7.5f, 7.5f)); add(poly(5.5f to -3.9f, 14.5f to -3.9f, 10f to 4f)) }
                "eclipseur" -> { add(minus(circle(-0.5f, 0f, 11f), circle(4.5f, 0f, 9.5f))); add(circle(8.5f, 0f, 3f)) }
                "rafaleur" -> { add(bar(-14f, 6f, 7f)); add(bar(-10f, 14f, 0f)); add(bar(-14f, 2f, -7f)) }
                "telekinesie" -> { add(circle(0f, 7f, 5f)); add(poly(-8f to -12f, 8f to -12f, 0f to -1f)) }
                "invocateur" -> { add(minus(diamond(17f), diamond(11.5f))); add(rect(0f, 0f, 6f, 6f)) }
                "teleporteur" -> {
                    add(poly(2.5f to 13f, -7.5f to -1f, -1.5f to -1f, -4.5f to -13f, 7.5f to 2f, 1.5f to 2f))
                    add(circle(-9f, 8f, 2f)); add(circle(9f, -8f, 2f))
                }
                "guerison" -> { add(minus(circle(0f, 0f, 12f), circle(0f, 0f, 9.5f))); add(rect(0f, 0f, 14f, 4f)); add(rect(0f, 0f, 4f, 14f)) }
                // New talents.
                "flasheur" -> { // a sun: light
                    add(circle(0f, 0f, 5.5f))
                    for (i in 0 until 8) add(ray(i * 45f, 8f, 13.5f, 2.6f))
                }
                "empathe" -> { // a heart
                    add(circle(-4.6f, 3f, 5.6f)); add(circle(4.6f, 3f, 5.6f))
                    add(poly(-9.9f to 1.2f, 9.9f to 1.2f, 0f to -11f))
                }
                "hydrokinesiste" -> { add(circle(0f, -3.5f, 7.5f)); add(poly(-6.6f to -0.2f, 6.6f to -0.2f, 0f to 13f)) } // a drop
                "givreur" -> { // a snowflake
                    for (i in 0 until 6) {
                        val a = i * 60f + 90f
                        add(ray(a, 0f, 13f, 2.6f))
                        val r = Math.toRadians(a.toDouble())
                        val bx = 8f * kotlin.math.cos(r).toFloat(); val by = 8f * kotlin.math.sin(r).toFloat()
                        add(seg(bx, by, a + 45f, 4.5f, 2f)); add(seg(bx, by, a - 45f, 4.5f, 2f))
                    }
                }
                "instillateur" -> { // a heart of emotions spreading out
                    add(circle(0f, 0f, 3.8f))
                    for (i in 0 until 6) add(circle(9.5f * kotlin.math.cos(Math.toRadians(i * 60.0 + 90)).toFloat(), 9.5f * kotlin.math.sin(Math.toRadians(i * 60.0 + 90)).toFloat(), 2.3f))
                }
                "technopathe" -> { // a chip
                    add(minus(rect(0f, 0f, 13f, 13f), rect(0f, 0f, 6f, 6f)))
                    for (v in listOf(-4f, 0f, 4f)) {
                        add(rect(v, 9.5f, 2f, 4f)); add(rect(v, -9.5f, 2f, 4f)); add(rect(9.5f, v, 4f, 2f)); add(rect(-9.5f, v, 4f, 2f))
                    }
                }
                "tenebreux" -> { // a ring half filled with shadow
                    add(minus(circle(0f, 0f, 12f), circle(0f, 0f, 9.8f)))
                    add(inter(circle(0f, 0f, 7.5f), rect(-5f, 0f, 10f, 16f)))
                }
                "chargeur" -> { // a battery
                    add(minus(rect(-1.5f, 0f, 21f, 12f), rect(-1.5f, 0f, 17f, 8f)))
                    add(rect(10.5f, 0f, 3f, 5f))
                    add(rect(-6.5f, 0f, 4f, 5.5f)); add(rect(-1.5f, 0f, 4f, 5.5f))
                }
                "phaseur" -> { // an arrow passing through a wall
                    add(rect(0f, 8f, 3f, 9f)); add(rect(0f, -8f, 3f, 9f))
                    add(bar(-13f, 6f, 0f, 2.6f)); add(poly(6f to 4.5f, 6f to -4.5f, 13.5f to 0f))
                }
                "hypnotiseur" -> { // concentric circles
                    add(minus(circle(0f, 0f, 12.5f), circle(0f, 0f, 10.3f)))
                    add(minus(circle(0f, 0f, 7.8f), circle(0f, 0f, 5.6f)))
                    add(circle(0f, 0f, 2.6f))
                }
                "fluctuateur" -> { // an hourglass: heavy and light
                    add(poly(-8.5f to 12f, 8.5f to 12f, 0f to 0.5f)); add(poly(-8.5f to -12f, 8.5f to -12f, 0f to -0.5f))
                    add(rect(0f, 13f, 19f, 2f)); add(rect(0f, -13f, 19f, 2f))
                }
                "vociferateur" -> { // a voice and its waves
                    add(circle(-9f, 0f, 3.2f))
                    val wedge = poly(-9f to 0f, 16f to 15f, 16f to -15f)
                    add(inter(minus(circle(-9f, 0f, 9f), circle(-9f, 0f, 6.8f)), wedge))
                    add(inter(minus(circle(-9f, 0f, 15f), circle(-9f, 0f, 12.8f)), wedge))
                }
                else -> add(diamond(16f))
            }
        }
        return b.parts
    }

    fun DrawScope.drawSymbol(id: String, color: Color, sizeFraction: Float = 0.8f) {
        val s = minOf(size.width, size.height) * sizeFraction
        paths(id, center.x, center.y, s).forEach { drawPath(it, color) }
    }
}

