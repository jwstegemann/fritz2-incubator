import dev.fritz2.binding.RootStore
import dev.fritz2.components.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.P
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.render
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.BoxParams
import dev.fritz2.styling.params.styled
import dev.fritz2.styling.theme.Theme
import kotlinx.coroutines.flow.map
import org.w3c.dom.events.MouseEvent

fun RenderContext.showcaseHeader(text: String) {
    (::h1.styled {
        fontFamily { "Inter, sans-serif" }
        margins {
            top { "2rem" }
            bottom { ".25rem" }
        }
        lineHeight { Theme().lineHeights.tiny }
        fontWeight { "700" }
        fontSize { Theme().fontSizes.huge }
        letterSpacing { Theme().letterSpacings.small }
    }) { +text }
}

fun RenderContext.paragraph(init: P.() -> Unit): P {
    return (::p.styled {
        fontFamily { "Inter, sans-serif" }
        margins {
            top { "1.25rem" }
        }
        lineHeight { Theme().lineHeights.larger }
        fontWeight { "400" }
        fontSize { Theme().fontSizes.normal }
        letterSpacing { Theme().letterSpacings.small }
    })  {
        init()
    }
}

fun RenderContext.componentFrame(init: Div.() -> Unit): Div { //Auslagerung von Komponente
    return (::div.styled {
        width { "100%" }
        margins {
            top { "1.25rem" }
        }
        border {
            width { thin }
            color { gray300 }
        }
        radius { larger }
        padding { normal }
    }){
        init()
    }
}

fun RenderContext.contentFrame(init: Div.() -> Unit): Div {
    return (::div.styled {
        margins {
            top { "2rem" }
        }
        maxWidth { "48rem" }
        paddings {
            top { huge }
            left { normal }
        }
    }){
        init()
    }
}


class MyCustomEntriesContext : MenuEntriesContext() {

    class RadioGroupContext {
        val items = ComponentProperty(listOf<String>())

        fun build() = object : MenuEntry {
            override fun render(
                context: RenderContext,
                styling: BoxParams.() -> Unit,
                baseClass: StyleClass,
                id: String?,
                prefix: String
            ) {
                context.apply {
                    radioGroup(items = items.value, styling = {
                        margins {
                            horizontal { small }
                            vertical { smaller }
                        }
                    })
                }
            }
        }
    }

    fun radios(expression: RadioGroupContext.() -> Unit) = RadioGroupContext()
        .apply(expression)
        .build()
        .also(::addEntry)
}


fun main() {

    val clickCounterStore = object : RootStore<Int>(0) {
        val increment = handle<MouseEvent> { current, _ -> current + 1 }
    }

    render("#target") {
        h1 { +"fritz incubator - Demo" }
        div {
            menu(entriesContextProvider = { MyCustomEntriesContext() }) {
                toggle {
                    pushButton {
                        text(clickCounterStore.data.map { "Click-counter: $it" })
                    }
                }
                placement { bottom }
                items {
                    item {
                        leftIcon { add }
                        text("Increment the click-counter")
                    } handledBy clickCounterStore.increment

                    divider()
                    subheader("Some more items:")
                    item {
                        leftIcon { circleInformation }
                        text("Info")
                    }
                    item {
                        leftIcon { circleHelp }
                        text("Help")
                    }
                    divider()
                    subheader("Custom menu entries:")
                    custom {
                        checkboxGroup(
                            items = listOf("Item 1", "Item 2")
                        )
                    }
                    custom {
                        spinner { }
                    }
                    divider()
                    subheader("Custom MenuEntryContext")
                    radios {
                        items(listOf("Item 1", "Item 2", "Item 3"))
                    }
                }
            }
        }

        tableDemo()
    }
}


fun title(s: String) {

}
