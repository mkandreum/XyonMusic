package com.xyonmusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Material "Liquid Glass" (iOS 26): relleno translúcido + borde especular + brillo superior. */
fun Modifier.liquidGlass(shape: Shape, dark: Boolean = true): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xCC2C2C30), Color(0xE61C1C1F)) else listOf(Color(0xE6FFFFFF), Color(0xD9F2F2F7))
        )
    )
    .border(
        0.8.dp,
        Brush.verticalGradient(
            if (dark) listOf(Color(0x59FFFFFF), Color(0x14FFFFFF), Color(0x26FFFFFF))
            else listOf(Color(0xFFFFFFFF), Color(0x1A000000))
        ),
        shape
    )

fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Large title de iOS ("Home") con icono de dispositivo y avatar (abre Ajustes). */
@Composable
fun LargeTitleHeader(
    title: String,
    avatarUrl: String?,
    initial: String,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    onDevice: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 18.dp, top = 30.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, Modifier.weight(1f),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 36.sp, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onDevice != null) {
            Icon(
                Icons.Outlined.PhoneAndroid, "Dispositivo",
                Modifier.size(30.dp).noRippleClick(onDevice), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            Spacer(Modifier.width(20.dp))
        }
        Avatar(avatarUrl, initial, 44.dp, Modifier.noRippleClick(onAvatar))
    }
}

@Composable
fun Avatar(url: String?, initial: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF8E8E93), Color(0xFF48484A))))
            .border(1.dp, Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isNotBlank()) Text(initial.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        if (url != null) AsyncImage(url, "Perfil", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 12.dp)
            .then(if (onMore != null) Modifier.noRippleClick(onMore) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        if (onMore != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
    }
}

/** Carátula con fallback de nota musical. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Color(0x66FFFFFF), modifier = Modifier.fillMaxSize(0.35f))
        if (model != null) AsyncImage(model, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

/** Tarjeta cuadrada con título sobre degradado inferior (Recently Played). */
@Composable
fun OverlayTile(
    title: String, subtitle: String?, size: Dp, modifier: Modifier = Modifier,
    onClick: () -> Unit, art: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(14.dp))
            .border(0.6.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        art()
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Mosaic(models: List<Any?>, modifier: Modifier = Modifier) {
    val m = (models + List(4) { null }).take(4)
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Artwork(m[0], Modifier.weight(1f).fillMaxHeight(), 0.dp); Artwork(m[1], Modifier.weight(1f).fillMaxHeight(), 0.dp)
        }
        Row(Modifier.weight(1f)) {
            Artwork(m[2], Modifier.weight(1f).fillMaxHeight(), 0.dp); Artwork(m[3], Modifier.weight(1f).fillMaxHeight(), 0.dp)
        }
    }
}

