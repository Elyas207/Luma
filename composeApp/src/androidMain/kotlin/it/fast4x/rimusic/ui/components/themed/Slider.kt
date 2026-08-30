package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.annotation.IntRange
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.fast4x.rimusic.colorPalette

@Composable
fun Slider(
    isEnabled: Boolean = true,
    state: Float,
    setState: (Float) -> Unit,
    onSlideComplete: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    @IntRange(from = 0) steps: Int = 0
) {
    androidx.compose.material3.Slider(
        enabled = isEnabled,
        value = state,
        onValueChange = setState,
        onValueChangeFinished = onSlideComplete,
        valueRange = range,
        modifier = modifier,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = LumaColor.Ground,
            activeTrackColor = LumaColor.Ember,
            inactiveTrackColor = LumaColor.Ink.copy(alpha = 0.75f),
            disabledThumbColor = LumaColor.Ink.copy(alpha = 0.4f),
            disabledActiveTrackColor = LumaColor.Ink.copy(alpha = 0.4f),
            disabledInactiveTrackColor = LumaColor.Ink.copy(alpha = 0.4f)
        )
    )
}
