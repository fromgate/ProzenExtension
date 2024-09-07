package studio

import kotlinx.datetime.Instant
import common.Requester

class Informer (val requester: Requester) {

    suspend fun getData(): InformerData? {
        val strikes = requester.getStrikesInfo()?.second



        return null
    }




}

/*
ПРОДЗЕН-инфо
Предупреждения: 0
Канал не ограничен
Канал индексируется
Охват подписчиков (SCR): 11,6%
Заблокировано читателей: 12
Статистика от 05.09.24 06:00
Курс минуты 03.09.24: ↓️0.055₽
🔗 Подписка в ZenReader
 */

data class InformerData (
    val strikes: Int?,
    val channelLimited: Boolean?,
    val channelIndexed: Boolean?,
    val scr: Double?,
    val blockedReaders: Int?,
    val statsTime: Instant?,
    val minuteCourse: Double?,
    val zenReaderUrl: String?
)