package com.rommapp.rommp.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Felt

/**
 * The fraction of the disc diameter given to the album art. Wider than a real
 * 7" label so the game's cover reads at handheld size; the groove band starts
 * just outside it.
 */
private const val LABEL_FRACTION = 0.47f

/**
 * A real-feeling 12" record. The disc + label rotate together; the specular sheen
 * is a separate stationary overlay so the light appears fixed while the record
 * turns (the key cue that sells "real vinyl"). Grooves span label-edge to the
 * run-out lip.
 */
@Composable
fun Vinyl(coverUrl: String?, rotation: Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(22.dp, CircleShape, clip = false)
                .rotate(rotation),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)

                // Disc base with a subtle center-to-edge vignette.
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF15120E),
                            0.62f to Color(0xFF100D0A),
                            1f to Color(0xFF090705),
                        ),
                        center = c,
                        radius = radius,
                    ),
                    radius = radius,
                    center = c,
                )

                // Fine grooves from label edge to the outer run-out.
                val step = 2.5.dp.toPx().coerceAtLeast(1.5f)
                var r = radius * (LABEL_FRACTION + 0.02f)
                var i = 0
                while (r < radius * 0.955f) {
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFF1B1712) else Color(0xFF0F0C0A),
                        radius = r,
                        center = c,
                        style = Stroke(width = 1f),
                    )
                    r += step
                    i++
                }
                // A few wider track-separator gaps.
                listOf(0.62f, 0.75f, 0.88f).forEach { f ->
                    drawCircle(Color(0xFF0A0806), radius = radius * f, center = c, style = Stroke(2f))
                }
                // Outer run-out lip.
                drawCircle(Color(0xFF2A2A30), radius = radius * 0.985f, center = c, style = Stroke(2f))
            }

            // Label = album art, 0.38 x diameter.
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(LABEL_FRACTION).clip(CircleShape),
                )
            } else {
                Box(Modifier.fillMaxSize(LABEL_FRACTION).clip(CircleShape).background(Felt))
            }

            // Spindle hole + brass pin.
            Box(Modifier.fillMaxSize(0.03f).clip(CircleShape).background(Color(0xFF090705)))
            Box(Modifier.size(4.dp).clip(CircleShape).background(Brass))
        }

        // Stationary specular sheen — a soft light bloom fixed in the top-left.
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(size.width * 0.30f, size.height * 0.24f),
                    radius = radius * 1.15f,
                ),
                radius = radius,
                center = c,
            )
        }
    }
}
