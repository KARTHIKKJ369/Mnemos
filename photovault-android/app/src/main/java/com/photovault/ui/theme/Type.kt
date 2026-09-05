package com.photovault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.photovault.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val InterFont = GoogleFont("Inter")
val RobotoMonoFont = GoogleFont("Roboto Mono")

val InterFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold)
)

val RobotoMonoFontFamily = FontFamily(
    Font(googleFont = RobotoMonoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = RobotoMonoFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = RobotoMonoFont, fontProvider = provider, weight = FontWeight.Bold)
)

// Backwards-compatible aliases
val PoppinsFontFamily = InterFontFamily
val SpaceGroteskFontFamily = InterFontFamily

/**
 * Strict 7-Step Typographic Scale (11, 13, 15, 17, 22, 28, 34sp)
 */
object MnemosType {
    // 34sp Display
    val Display34 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        letterSpacing = (-0.02).em
    )

    // 28sp Headline
    val Headline28 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        letterSpacing = (-0.01).em
    )

    // 22sp Title
    val Title22 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 0.em
    )

    // 17sp Body Large
    val BodyLarge17 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = 0.em
    )

    // 15sp Body Regular
    val Body15 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.em
    )

    // 13sp Body Small / Secondary
    val BodySmall13 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.01.em
    )

    // 11sp Label (All-caps section headers: +0.06em tracking)
    val Label11 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.06.em
    )

    // Monospace Scale (Exclusively for file sizes, timestamps, resolution, device technical details, sync counts)
    val Mono11 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.em
    )

    val Mono13 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.em
    )

    val Mono15 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.em
    )
}

val PhotoVaultTypography = Typography(
    displayLarge = MnemosType.Display34,
    displayMedium = MnemosType.Headline28,
    headlineMedium = MnemosType.Headline28,
    titleLarge = MnemosType.Title22,
    titleMedium = MnemosType.BodyLarge17.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = MnemosType.Body15.copy(fontWeight = FontWeight.Medium),
    bodyLarge = MnemosType.BodyLarge17,
    bodyMedium = MnemosType.Body15,
    bodySmall = MnemosType.BodySmall13,
    labelLarge = MnemosType.BodySmall13.copy(fontWeight = FontWeight.Medium),
    labelMedium = MnemosType.Label11,
    labelSmall = MnemosType.Mono11
)
