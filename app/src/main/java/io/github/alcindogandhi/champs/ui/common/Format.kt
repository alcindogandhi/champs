package io.github.alcindogandhi.champs.ui.common

import android.content.res.Resources
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.data.remote.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private fun zone() = ZoneId.systemDefault()

    fun time(instant: Instant?): String =
        instant?.atZone(zone())?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()

    /** "sáb., 22/11" */
    fun shortDate(instant: Instant?): String =
        instant?.atZone(zone())?.format(DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.getDefault())).orEmpty()

    /** "sábado, 22 de novembro de 2026" */
    fun longDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

    fun dateTime(instant: Instant?): String = instant?.let {
        it.atZone(zone()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }.orEmpty()

    fun localDate(instant: Instant?): LocalDate? = instant?.atZone(zone())?.toLocalDate()

    fun fetchedAt(millis: Long): String = time(Instant.ofEpochMilli(millis))

    fun error(resources: Resources, error: Throwable): String = when (error) {
        is HttpException -> resources.getString(R.string.error_http, error.code)
        is IOException -> resources.getString(R.string.no_internet_connection)
        else -> resources.getString(R.string.error_generic)
    }
}
