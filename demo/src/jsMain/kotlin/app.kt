import dev.fritz2.binding.RootStore
import dev.fritz2.components.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.P
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.render
import dev.fritz2.styling.params.plus
import dev.fritz2.styling.params.styled
import dev.fritz2.styling.theme.Theme

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
            color { lightGray }
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

fun main() {

    render("#target") {
        h1 { +"fritz incubator - Demo" }
        div {
            val menuClickStore = object : RootStore<Unit>(Unit) {
                val log = handle<Unit> { _, _ -> console.log("Menu item clicked!") }
            }

            menu {
                toggle {
                    pushButton {
                        text("Test")
                    }
                }
                placement { right }
                items {
                    menuCheckboxGroup(
                        title = "Checkboxes",
                        options = listOf("Option 1", "Option 2", "Option 3")
                    )

                    menuDivider()

                    menuRadioGroup(
                        title = "Radios",
                        options = listOf("Option 1", "Option 2", "Option 3")
                    )

                    menuDivider()

                    menuItem {
                        leftIcon { ban }
                        label("This is a simple menu-item.")
                    } handledBy menuClickStore.log

                    menuGroup {
                        title("Menu-Group")
                        items {
                            menuItem {
                                leftIcon { link }
                                label("Menu-item in a group.")
                            }
                            menuItem {
                                leftIcon { camera }
                                label("Menu-item in a group 2.")
                            }
                        }
                    }
                }
            }
        }

        tableDemo()
    }
}