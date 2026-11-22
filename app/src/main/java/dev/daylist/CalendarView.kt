package dev.daylist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.*
import java.time.format.*
import java.text.NumberFormat

internal fun calendarMonth(tasks:List<JSONObject>, month:YearMonth):Map<LocalDate,List<JSONObject>> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val from = first.toString()
    val until = last.toString()
    val days = mutableMapOf<LocalDate,MutableList<JSONObject>>()
    for (task in tasks) {
        val date = task.str("date")
        if (date.isEmpty() || date > until) continue
        val recurring = task.str("repeat").isNotEmpty() && !task.optBoolean("done")
        if (!recurring && date < from) continue
        val start = LocalDate.parse(date)
        var occurrence:LocalDate? = if (recurring) Reminders.next(task,start,first) else start
        while (occurrence != null && occurrence <= last) {
            days.getOrPut(occurrence){ mutableListOf() }.add(task)
            occurrence = if (recurring) Reminders.next(task,start,occurrence.plusDays(1)) else null
        }
    }
    return days
}

@Composable internal fun CalendarView(tasks:List<JSONObject>, selected:LocalDate, today:LocalDate,
    select:(LocalDate)->Unit, row:@Composable (JSONObject,Boolean)->Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val month = YearMonth.from(selected)
    val first = month.atDay(1)
    val firstWeekday = remember(locale) {
        val androidDay = android.icu.util.Calendar.getInstance(locale).firstDayOfWeek
        DayOfWeek.of((androidDay + 5) % 7 + 1)
    }
    val offset = (first.dayOfWeek.value - firstWeekday.value + 7) % 7
    val byDay = remember(month,tasks){calendarMonth(tasks,month)}
    val agenda = remember(byDay,selected,locale){byDay[selected].orEmpty().sortedWith(compareBy({it.optBoolean("done")},{it.str("time")},{it.str("title").lowercase(locale)}))}
    val monthFormat = remember(locale){DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale,"yMMMM"),locale)}
    val full = remember(locale){DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)}
    val numbers = remember(locale){NumberFormat.getIntegerInstance(locale)}
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = MaterialTheme.colorScheme
    LazyColumn(contentPadding=PaddingValues(bottom=88.dp)){
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal=8.dp).pointerInput(selected,rtl){
                var distance=0f
                detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={
                    if(kotlin.math.abs(distance)>48.dp.toPx()) select(selected.plusMonths(if((distance<0) xor rtl) 1L else -1L))
                }){change,delta->change.consume();distance+=delta}
            }){
                Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(month.format(monthFormat),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={select(today)}){Text("Today")}
                    IconButton(onClick={select(selected.minusMonths(1))}){Icon(if(rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,"Previous month")}
                    IconButton(onClick={select(selected.plusMonths(1))}){Icon(if(rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,"Next month")}
                }
                Row {
                    repeat(7){index->
                        Box(Modifier.weight(1f).padding(vertical=8.dp),contentAlignment=Alignment.Center){
                            Text(firstWeekday.plus(index.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE,locale),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                        }
                    }
                }
                repeat((offset+month.lengthOfMonth()+6)/7){week->
                    Row {
                        repeat(7){column->
                            val number = week*7+column-offset+1
                            if(number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                            else {
                                val date = month.atDay(number)
                                val count = byDay[date]?.size ?: 0
                                val chosen = date==selected
                                val current = date==today
                                val label = (if(current)"Today, " else "")+date.format(full)+", "+count+(if(count==1)" task" else " tasks")
                                Column(Modifier.weight(1f).heightIn(min=48.dp).selectable(chosen,role=Role.Button,onClick={select(date)})
                                    .semantics(mergeDescendants=true){contentDescription=label},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                    Box(Modifier.clip(CircleShape).background(when{chosen->colors.primary;current->colors.secondaryContainer;else->colors.surface}).sizeIn(minWidth=32.dp,minHeight=32.dp),contentAlignment=Alignment.Center){
                                        Text(numbers.format(number),Modifier.clearAndSetSemantics{},style=MaterialTheme.typography.bodyMedium,color=when{chosen->colors.onPrimary;current->colors.onSecondaryContainer;else->colors.onSurface})
                                    }
                                    Row(Modifier.height(8.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){
                                        repeat(minOf(count,3)){Spacer(Modifier.size(3.dp).background(colors.primary,CircleShape))}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top=8.dp))
            Heading(selected.format(full))
        }
        if(agenda.isEmpty())item{Text("No tasks scheduled",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
        items(agenda,key={it.str("id")}){task->row(task,task.str("date")!=selected.toString())}
    }
}
