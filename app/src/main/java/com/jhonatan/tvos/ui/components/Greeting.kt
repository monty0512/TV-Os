package com.jhonatan.tvos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Sky
import com.jhonatan.tvos.data.Weather
import com.jhonatan.tvos.data.WeatherNow
import java.util.Calendar

/** "Buenos días / Buenas tardes / Buenas noches, [nombre]" según la hora. */
@Composable
fun greeting(name: String, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
    val (withName, alone) = when (hour) {
        in 5..11 -> R.string.greeting_morning to R.string.greeting_morning_alone
        in 12..18 -> R.string.greeting_afternoon to R.string.greeting_afternoon_alone
        else -> R.string.greeting_night to R.string.greeting_night_alone
    }
    return if (name.isBlank()) stringResource(alone) else stringResource(withName, name)
}

/** "22° · Despejado · Medellín". */
@Composable
fun weatherText(w: WeatherNow): String {
    val sky = stringResource(
        when (Weather.sky(w.code)) {
            Sky.CLEAR -> R.string.wx_clear
            Sky.CLOUDY -> R.string.wx_cloudy
            Sky.FOG -> R.string.wx_fog
            Sky.RAIN -> R.string.wx_rain
            Sky.SNOW -> R.string.wx_snow
            Sky.STORM -> R.string.wx_storm
        },
    )
    return listOf("${w.tempC}°", sky, w.city).filter { it.isNotBlank() }.joinToString(" · ")
}
