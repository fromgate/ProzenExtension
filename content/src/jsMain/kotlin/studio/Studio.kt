package studio

import ContentRunner
import common.Requester

class Studio (val requester: Requester): ContentRunner {
    override fun run() {
        val informer = Informer(requester)
        informer.create()


        // 1. Отрисовка меню
        // 2. Отрисовка информера
        // 3. Карточки расширенной статистики

    }


}