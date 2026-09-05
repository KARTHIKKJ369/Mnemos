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

val SpaceGroteskFont = GoogleFont("Space Grotesk")
val InterFont = GoogleFont("Inter")
val RobotoMonoFont = GoogleFont("Roboto Mono")

val SpaceGroteskFontFamily = FontFamily(
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = SpaceGroteskFont, fontProvider = provider, weight = FontWeight.Bold)
)

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
val PoppinsFontFamily = SpaceGroteskFontFamily

/**
 * FRAME // OS Typographic Scale
 */
object MnemosType {
    // 32sp Hero Display (e.g. 135H 56M // 05 TITLES)
    val Display34 = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.02).em
    )

    // 24sp Hero Headline
    val Headline28 = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.02).em
    )

    // 20sp Page Title
    val Title22 = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.01).em
    )

    val PageTitle20 = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.01).em
    )

    // 15sp Card Title
    val CardTitle15 = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.em
    )

    // 15sp Body Regular
    val Body15 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.em
    )

    val BodyLarge17 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = 0.em
    )

    // 13sp Body Secondary
    val BodySecondary13 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.01.em
    )

    val BodySmall13 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.01.em
    )

    // 11sp Label (All-caps section headers: +0.06em tracking)
    val Label11 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.06.em
    )

    // Monospace Scales (e.g. 00 COMPLETED // 00 WATCHING // 05 QUEUED)
    val Mono11 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.02.em
    )

    val Mono12 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.02.em
    )

    val Mono13 = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.02.em
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
    titleMedium = MnemosType.PageTitle20,
    titleSmall = MnemosType.CardTitle15,
    bodyLarge = MnemosType.BodyLarge17,
    bodyMedium = MnemosType.Body15,
    bodySmall = MnemosType.BodySmall13,
    labelLarge = MnemosType.BodySmall13.copy(fontWeight = FontWeight.Medium),
    labelMedium = MnemosType.Label11,
    labelSmall = MnemosType.Mono12
)
