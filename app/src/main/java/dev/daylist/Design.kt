package dev.daylist

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat

internal object Metrics {
    val gutter = 16.dp
    val gap = 8.dp
    val section = 24.dp
    val touch = 48.dp
    val panel = RoundedCornerShape(24.dp)
}
private val light = lightColorScheme(primary=Color(0xff3869c5), background=Color(0xfffafaf8), surface=Color(0xfffafaf8), onSurface=Color(0xff202124), onSurfaceVariant=Color(0xff535c68), surfaceContainer=Color(0xfff0f2f5), primaryContainer=Color(0xffdbe7ff), onPrimaryContainer=Color(0xff142e58))
private val dark = darkColorScheme(primary=Color(0xffa8c7fa), background=Color(0xff121316), surface=Color(0xff121316), onSurface=Color(0xffe8eaed), onSurfaceVariant=Color(0xffb5bbc4), surfaceContainer=Color(0xff1c1d21), primaryContainer=Color(0xff263c61), onPrimaryContainer=Color(0xffdae6ff))
@Composable internal fun DayTheme(a:Main, mode:String, content:@Composable ()->Unit){
    val night=mode=="Dark" || mode=="System" && isSystemInDarkTheme()
    val colors=if(Build.VERSION.SDK_INT>=31) {if(night) dynamicDarkColorScheme(a) else dynamicLightColorScheme(a)} else if(night) dark else light
    SideEffect { WindowInsetsControllerCompat(a.window,a.window.decorView).apply {isAppearanceLightStatusBars=!night;isAppearanceLightNavigationBars=!night} }
    MaterialTheme(colorScheme=colors,content=content)
}
@Composable internal fun Heading(text:String){
    Text(text,Modifier.padding(start=Metrics.gutter,top=Metrics.section,bottom=Metrics.gap),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
}
