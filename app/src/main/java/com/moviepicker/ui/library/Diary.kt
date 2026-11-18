package com.moviepicker.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.RatingScale
import com.moviepicker.domain.Title
import com.moviepicker.domain.parseDate
import com.moviepicker.ui.common.Poster
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.RatingBadge
import com.moviepicker.ui.design.SectionHeader
import com.moviepicker.ui.design.TicketField
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.VerticalPerforation
import com.moviepicker.ui.design.pressClickable
import com.moviepicker.ui.design.ratingColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", ru)
private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)

/** Дневник: всё, что понравилось или оценено, по месяцам. Дата — день отметки. Сверху — короткая статистика. */
@Composable
fun DiaryList(
    watched: List<UserTitleEntity>,
    onOpen: (Title) -> Unit,
    byRating: Boolean = false,
    sortBar: @Composable () -> Unit = {},
) {
    // По дате — группы по месяцам; по оценке — группы «10 · Шедевр», «9 · Отлично»…, без оценки в конце
    val groups: List<Pair<String, List<UserTitleEntity>>> = remember(watched, byRating) {
        if (byRating) {
            watched
                .sortedWith(compareByDescending<UserTitleEntity> { it.userRating }.thenByDescending { it.diaryDate })
                .groupBy { it.userRating }
                .map { (r, list) -> (if (r > 0) "$r · ${RatingScale.label(r)}" else "Без оценки") to list }
        } else {
            watched
                .sortedWith(compareByDescending<UserTitleEntity> { it.diaryDate }.thenByDescending { it.updatedAt })
                .groupBy { YearMonth.from(it.diaryDate) }
                .map { (month, list) -> month.format(monthTitle).replaceFirstChar { it.uppercase() } to list }
        }
    }
    val rated = watched.filter { it.userRating > 0 }
    val thisYear = watched.count { it.diaryDate.year == LocalDate.now().year }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TicketField("Всего", watched.size.toString(), Modifier.weight(1f))
                TicketField("В этом году", thisYear.toString(), Modifier.weight(1f))
                TicketField(
                    "Средняя",
                    if (rated.isEmpty()) "—" else "%.1f".format(rated.map { it.userRating }.average()),
                    Modifier.weight(1f),
                    valueColor = Brand.Gold,
                )
            }
        }
        item(key = "sort") {
            Box(Modifier.padding(horizontal = 16.dp)) { sortBar() }
        }
        groups.forEach { (title, items) ->
            item(key = "g:$title") {
                SectionHeader("$title · ${items.size}")
            }
            items(items, key = { "${it.type}:${it.id}" }) { entity ->
                DiaryRow(entity, onClick = { onOpen(entity.toTitle()) })
            }
        }
    }
}

@Composable
private fun DiaryRow(entity: UserTitleEntity, onClick: () -> Unit) {
    val date = entity.diaryDate
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TicketShape(notchRadius = 5.dp, cornerRadius = 6.dp, notchOffset = 56.dp, vertical = true, fromEnd = true))
            .background(Brand.Ticket)
            .pressClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(entity.posterPath, Modifier.width(48.dp), shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                entity.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val year = parseDate(entity.date)?.year
            Text(
                listOfNotNull(
                    if (entity.mediaType == MediaType.TV) "Сериал" else "Фильм",
                    year?.toString(),
                    "отмечено " + date.format(dayMonth),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted, maxLines = 1,
            )
            if (entity.userRating > 0) {
                Text(
                    RatingScale.label(entity.userRating).uppercase(),
                    style = PkType.tiny, color = ratingColor(entity.userRating),
                )
            }
        }
        VerticalPerforation(inset = 6.dp)
        Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (entity.userRating > 0) RatingBadge(entity.userRating, large = true)
            else Text("—", style = PkType.number, color = Brand.TextMuted)
        }
    }
}
