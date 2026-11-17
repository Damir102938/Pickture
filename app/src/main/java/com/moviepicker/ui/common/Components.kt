package com.moviepicker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moviepicker.domain.Images
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.ui.theme.Gold
import com.moviepicker.ui.theme.MatchHigh
import com.moviepicker.ui.theme.MatchLow
import com.moviepicker.ui.theme.MatchMid

@Composable
fun Poster(path: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
        AsyncImage(
            model = Images.poster(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

fun matchColor(match: Int): Color = when {
    match >= 75 -> MatchHigh
    match >= 55 -> MatchMid
    else -> MatchLow
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MarkIcon(mark: UserMark?, modifier: Modifier = Modifier) {
    val (icon, tint) = when {
        mark == null -> return
        mark.reaction == Reaction.LIKE -> Icons.Filled.Favorite to MaterialTheme.colorScheme.primary
        mark.reaction == Reaction.DISLIKE -> Icons.Filled.ThumbDown to MatchLow
        mark.inWatchlist -> Icons.Filled.Bookmark to Gold
        else -> return
    }
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

/** Карточка фильма: постер, рейтинг TMDB, процент совпадения и отметка пользователя. */
@Composable
fun TitleCard(
    title: Title,
    match: Int?,
    mark: UserMark?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            Poster(title.posterPath, Modifier.fillMaxWidth())
            if (title.voteCount >= 5 && title.rating > 0) {
                Chip(
                    "%.1f".format(title.rating), Gold, Modifier.align(Alignment.TopStart).padding(6.dp),
                    icon = Icons.Filled.Star,
                )
            }
            MarkIcon(mark, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (match != null) {
                Chip("$match%", matchColor(match), Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            title.subtitle(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Сетка карточек с подгрузкой следующей страницы при прокрутке до конца. */
@Composable
fun TitleGrid(
    items: List<Title>,
    matchOf: (Title) -> Int?,
    markOf: (TitleKey) -> UserMark?,
    onOpen: (Title) -> Unit,
    modifier: Modifier = Modifier,
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    state: LazyGridState = rememberLazyGridState(),
    header: (@Composable () -> Unit)? = null,
) {
    val shouldLoad by remember(items.size, canLoadMore) {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && last >= state.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(items, key = { "${it.type.path}:${it.id}" }) { title ->
            TitleCard(title, matchOf(title), markOf(title.key), onClick = { onOpen(title) })
        }
        if (loadingMore) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, height: Dp? = null) {
    Box(
        (if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize()),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(
    title: String,
    text: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageBox("Не удалось загрузить", message, modifier, "Повторить", onRetry)
