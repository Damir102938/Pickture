package com.moviepicker.desktop.ui.design

import androidx.compose.ui.graphics.Color

/**
 * Фирменная палитра Pickture в стиле «Афиша»: индиго экрана + золото кинобилета.
 * Одинаковая на Android и ПК.
 */
object Brand {
    /** Фирменный индиго (фон иконки). */
    val Indigo = Color(0xFF5865F2)

    /** Светлый индиго для акцентов на тёмном фоне. */
    val Accent = Color(0xFF8C96FF)

    /** Золото билета: главные кнопки, активная вкладка, рейтинг. */
    val Gold = Color(0xFFFFC857)
    val OnGold = Color(0xFF2B2000)

    val Background = Color(0xFF0F1020)

    /** «Бумага» билета — фон карточек и панелей. */
    val Ticket = Color(0xFF181A2C)
    val TicketHigh = Color(0xFF23253A)

    /** Линия перфорации и тонкие рамки. */
    val Perforation = Color(0xFF3A3D57)

    val Text = Color(0xFFE6E7F5)
    val TextMuted = Color(0xFF9A9CB8)

    val MatchHigh = Color(0xFF4CD08A)
    val MatchMid = Color(0xFFFFB547)
    val MatchLow = Color(0xFF9A9CB8)

    fun match(percent: Int): Color = when {
        percent >= 75 -> MatchHigh
        percent >= 55 -> MatchMid
        else -> MatchLow
    }
}
