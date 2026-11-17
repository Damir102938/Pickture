package com.moviepicker.desktop.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Текстовые стили «Афиши». Заголовки и метки — Oswald, основной текст — системный шрифт. */
object PkType {
    /** Заголовок экрана: «ЧТО НОВОГО». */
    val screenTitle = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.4.sp)

    /** Надстрочник над заголовком: «PICKTURE · АФИША». */
    val kicker = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 2.sp)

    /** Заголовок раздела: «01 — ЦИФРОВЫЕ РЕЛИЗЫ». */
    val section = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.6.sp)

    /** Шапка вложенного экрана. */
    val barTitle = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)

    /** Кнопки, вкладки, переключатели. */
    val label = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)

    /** Мелкие метки на билетах и в навигации. */
    val tiny = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.6.sp)

    /** Цифры: проценты совпадения, рейтинг, номера разделов. */
    val number = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)

    /** Крупное название в карточке фильма. */
    val hero = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.3.sp)
}

/** Типографика Material, в которой заголовки диалогов и крупные надписи тоже набраны Oswald. */
fun picktureTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Oswald, fontWeight = FontWeight.Medium),
    )
}
