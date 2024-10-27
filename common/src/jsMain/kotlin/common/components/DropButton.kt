package common.components

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.js.onClickFunction

import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.EventListener


fun TagConsumer<HTMLElement>.dropButton(
    buttonId: String,
    items: Map<String, String>,
    onSelect: (String) -> Unit,
    buttonContent: TagConsumer<HTMLElement>.() -> Unit
) {
    div {
        id = buttonId
        button(classes = "prozen-dropdown-menu-button") {
            id = "$buttonId-button"

            // Добавление произвольного содержимого кнопки через функцию buttonContent
            buttonContent()

            onClickFunction = {
                try {
                    val menu = document.getElementById("$buttonId-menu") as HTMLElement
                    if (menu.classList.contains("prozen-menu-open")) {
                        closeMenus()
                    } else {
                        menu.classList.toggle("prozen-menu-open")
                        if (menu.classList.contains("prozen-menu-open")) {
                            document.addEventListener(
                                "click",
                                createDocumentClickListener(),
                                true
                            )
                        }
                    }
                } catch (e: Exception) {
                    console.log(e)
                }
            }
        }

        ul("prozen-dropdown-menu") {
            id = "$buttonId-menu"
            style = "top: auto; right: auto; width: auto;"
            items.forEach { (itemId, title) ->
                li {
                    id = "$buttonId-menu-$itemId"
                    +title
                    onClickFunction = {
                        closeMenus()
                        onSelect(itemId)
                    }
                }
            }
        }
    }
}


/*
fun TagConsumer<HTMLElement>.dropButton(buttonId: String, buttonTitle: String, items: Map<String, String>, onSelect: (String) -> Unit) {
    div {
        id = buttonId
        button(classes = "prozen-dropdown-menu-button") {
            +buttonTitle
            id = "$buttonId-button"
            onClickFunction = {
                try {
                    val menu = document.getElementById("$buttonId-menu") as HTMLElement
                    if (menu.classList.contains("prozen-menu-open")) {
                        closeMenus()
                    } else {
                        menu.classList.toggle("prozen-menu-open")
                        if (menu.classList.contains("prozen-menu-open")) {
                            document.addEventListener(
                                "click",
                                createDocumentClickListener(),
                                true
                            )
                        }
                    }
                } catch (e: Exception) {
                    console.log(e)
                }

            }
        }

        ul("prozen-dropdown-menu") {
            id = "$buttonId-menu"
            style = "top: auto; right: auto; width: auto;"
            items.forEach { (itemId, title) ->
                li {
                    id = "$buttonId-menu-$itemId"
                    +title
                    onClickFunction = {
                        closeMenus()
                        onSelect(itemId)
                    }
                }
            }

        }
    }
}
*/
fun closeMenus() {
    val menus = document.getElementsByClassName("prozen-dropdown-menu").asList().filterIsInstance<HTMLElement>()
    menus.forEach { menu ->
        if (menu.classList.contains("prozen-menu-open")) {
            menu.classList.remove("prozen-menu-open")
        }
    }
    document.removeEventListener(
        "click",
        createDocumentClickListener(),
        true
    )
}

fun createDocumentClickListener(): EventListener {
    return EventListener { event ->
        val target = event.target as? HTMLElement
        if (target != null
            && !target.classList.contains("prozen-dropdown-menu")
            && (!target.classList.contains("prozen-dropdown-menu-button")
                    || target.parentElement?.classList?.contains("prozen-dropdown-menu-button") != true)
        ) {
            closeMenus()
        }
    }
}