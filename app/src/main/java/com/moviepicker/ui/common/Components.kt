package com.moviepicker.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ThumbDown
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moviepicker.domain.Images
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.MarqueeButton
import com.moviepicker.ui.design.MarqueeLoader
import com.moviepicker.ui.design.Perforation
import com.moviepicker.ui.design.RatingBadge
import androidx.compose.material.icons.filled.Visibility
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.pressClickable

/** Постер. [ratio] = null — без фиксированных пропорций (размер задаёт [modifier]). */
@Composable
fun Poster(
    path: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    ratio: Float? = 2f / 3f,
) {
    Box(
        modifier
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
            .clip(shape)
            .background(Brand.TicketHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Movie, null, tint = Brand.Perforation, modifier = Modifier.size(32.dp))
        AsyncImage(
            model = Images.poster(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

fun matchColor(match: Int): Color = Brand.match(match)

/** Отметка пользователя на постере — маленький штамп в углу. */
@Composable
fun MarkIcon(mark: UserMark?, modifier: Modifier = Modifier) {
    if (mark != null && mark.rating > 0) {
        RatingBadge(mark.rating, modifier)
        return
    }
    val (icon, tint) = when {
        mark == null -> return
        mark.watched -> Icons.Filled.Visibility to Brand.MatchHigh
        mark.reaction == Reaction.LIKE -> Icons.Filled.Favorite to Brand.Accent
        mark.reaction == Reaction.DISLIKE -> Icons.Filled.ThumbDown to Brand.MatchLow
        mark.inWatchlist -> Icons.Filled.Bookmark to Brand.Gold
        else -> return
    }
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

private val StubHeight = 24.dp

/**
 * Карточка-билет: постер, линия отрыва и корешок с процентом совпадения и рейтингом TMDB.
 * Под билетом — название и дата.
 */
@Composable
fun TitleCard(
    title: Title,
    match: Int?,
    mark: UserMark?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.pressClickable(onClick = onClick)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(TicketShape(notchRadius = 5.dp, cornerRadius = 6.dp, notchOffset = StubHeight, fromEnd = true))
                .background(Brand.Ticket),
        ) {
            Box {
                Poster(title.posterPath, Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp))
                MarkIcon(mark, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Perforation(inset = 6.dp)
            TicketStub(title, match)
        }
        Spacer(Modifier.height(6.dp))
        // Подпись всегда занимает место под две строки названия и дату: иначе высота ряда
        // скачет при прокрутке и сдвигает разделы ниже. Дата при этом идёт сразу под названием.
        val nameStyle = MaterialTheme.typography.bodyMedium
        val dateStyle = MaterialTheme.typography.bodySmall
        val captionHeight = with(LocalDensity.current) { nameStyle.lineHeight.toDp() * 2 + dateStyle.lineHeight.toDp() }
        Column(Modifier.heightIn(min = captionHeight)) {
            Text(
                title.name,
                style = nameStyle,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title.subtitle(),
                style = dateStyle,
                color = Brand.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TicketStub(title: Title, match: Int?) {
    Row(
        Modifier.fillMaxWidth().height(StubHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (match != null) {
            Text("$match%", style = PkType.number, color = matchColor(match))
        } else {
            Text(
                if (title.type == MediaType.TV) "СЕРИАЛ" else "ФИЛЬМ",
                style = PkType.tiny, color = Brand.TextMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (title.voteCount >= 5 && title.rating > 0) {
            Text("★ %.1f".format(title.rating), style = PkType.number, color = Brand.Gold)
        }
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
        columns = GridCells.Adaptive(108.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(items, key = { "${it.type.path}:${it.id}" }) { title ->
            TitleCard(title, matchOf(title), markOf(title.key), onClick = { onOpen(title) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    MarqueeLoader()
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
    ) { MarqueeLoader() }
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
        Text(title.uppercase(), style = PkType.section, color = Brand.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Perforation(Modifier.width(64.dp), color = Brand.Gold)
        if (text != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = Brand.TextMuted,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            MarqueeButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageBox("Не удалось загрузить", message, modifier, "Повторить", onRetry)
