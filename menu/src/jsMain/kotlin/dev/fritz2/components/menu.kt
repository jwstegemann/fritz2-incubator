package dev.fritz2.components

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.SimpleHandler
import dev.fritz2.binding.Store
import dev.fritz2.dom.DomListener
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.MouseEvent


private val menuEntryCss = staticStyle("menu-item") {
    width { "100%" }
    paddings {
        vertical { smaller }
    }
    radius { "6px" }
}

private val menuDividerCss = staticStyle("menu-divider") {
    width { "100%" }
    height { "1px" }
    margins { vertical { smaller } }
    background { color { lighterGray } }
}

private val menuOptionStyle: Style<BasicParams> = {
    margins { vertical { smaller } }
}


class MenuComponent {

    companion object {
        private val menuDropdownContainerCss = staticStyle("menu-dropdown-container") {
            position { relative { } }
        }

        private val menuDropdownCss = staticStyle("menu-dropdown") {
            position { absolute { } }
            zIndex { "100" }
            radius { "6px" }
            background { color { base } }

            paddings {
                horizontal { small }
                bottom { small }
            }
            minWidth { "20vw" }

            boxShadow { raised }

            // FIXME: Animation not working
            // fade-in-animation
            //opacity { "1" }
            //css("transition: opacity 1s ease-in-out;")
        }

        class VisibilityStore : RootStore<Boolean>(false) {
            val show = handle<Unit> { _, _ -> true }
            val dismiss = handle<Unit> { _, _ -> false }
        }
    }

    val toggle = ComponentProperty<RenderContext.() -> Unit> { }
    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)
    val visibilityStore = VisibilityStore()

    fun render(
        styling: BasicParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String,
        renderContext: RenderContext,
    ) {
        renderContext.apply {
            box(prefix = "menu") {

                box(prefix = "menu-toggle-container") {
                    toggle.value(this)
                    // TODO: Close menu when clicking outside
                    clicks.events.map { } handledBy visibilityStore.show
                }

                box(baseClass = menuDropdownContainerCss) {
                    visibilityStore.data.render { visible ->
                        if (visible)
                            box(
                                styling = styling,
                                baseClass = baseClass?.let { it + menuDropdownCss } ?: menuDropdownCss,
                                id = id,
                                prefix = prefix
                            ) {
                                items.value?.invoke(this)

                                // TODO: Remove once the menu can be dismissed via window event
                                clickButton {
                                    text("Dismiss (debug)")
                                    size { small }
                                    variant { outline }
                                } handledBy visibilityStore.dismiss
                            }
                        else
                            box { /* just an empty placeholder */ }
                    }
                }
            }
        }
    }
}

fun RenderContext.menu(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu",
    build: MenuComponent.() -> Unit,
) = MenuComponent()
    .apply(build)
    .render(styling, baseClass, id, prefix, this)


class MenuItemComponent : EventProperties<HTMLDivElement> by EventMixin() {

    companion object {
        private val menuItemStyle: Style<FlexParams> = {
            alignItems { AlignItemsValues.center }
            hover {
                background {
                    color { lightestGray }
                }
                css("filter: brightness(90%);")
            }
        }
    }

    val label = ComponentProperty<String?>(value = null)
    val leftIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)
    val rightIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)

    fun render(
        styling: BasicParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String,
        renderContext: RenderContext,
    ) {
        renderContext.apply {
            flexBox(
                styling = styling + menuItemStyle,
                baseClass = baseClass?.let { it + menuEntryCss } ?: menuEntryCss,
                id = id,
                prefix = prefix,
            ) {
                leftIcon.value?.let {
                    icon { fromTheme(it) }
                }
                label.value?.let {
                    (::label.styled {
                        margins { left { tiny } }
                    }) { +it }
                }
                rightIcon.value?.let {
                    icon { fromTheme(it) }
                }
                events.value.invoke(this)
            }
        }
    }
}

fun RenderContext.menuItem(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu-item",
    build: MenuItemComponent.() -> Unit,
): DomListener<MouseEvent, HTMLDivElement> {

    var clickListener: DomListener<MouseEvent, HTMLDivElement>? = null
    val component = MenuItemComponent()
        .apply(build)
        .apply {
            events {
                clickListener = clicks
            }
        }

    component.render(styling, baseClass, id, prefix, this)
    return clickListener!!
}


class MenuGroupComponent {
    val title = ComponentProperty<String?>(value = null)
    val titleStyle = ComponentProperty<Style<BasicParams>> { }
    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)

    fun render(
        styling: BasicParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String,
        renderContext: RenderContext,
    ) {
        renderContext.apply {
            stackUp(
                styling = styling,
                baseClass = baseClass?.let { it + menuEntryCss } ?: menuEntryCss,
                id = id,
                prefix = prefix
            ) {
                spacing { none }
                items {
                    title.value?.let {
                        (::h5.styled(styling = titleStyle.value + {
                            margins { bottom { smaller } }
                        })) { +it }
                    }
                    this@MenuGroupComponent.items.value?.invoke(this)
                }
            }
        }
    }
}

fun RenderContext.menuGroup(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu-item-group",
    build: MenuGroupComponent.() -> Unit,
) = MenuGroupComponent()
    .apply(build)
    .render(styling, baseClass, id, prefix, this)

fun RenderContext.menuCheckboxGroup(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu-checkbox-group",
    title: String,
    options: List<String>,
    store: Store<List<String>>? = null,
) {
    menuGroup(styling, baseClass, id, prefix) {
        title(title)
        titleStyle { margins { bottom { smaller } } }
        items {
            checkboxGroup(items = options, store = store) {
                itemStyle(menuOptionStyle)
            }
        }
    }
}

fun RenderContext.menuRadioGroup(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu-radio-group",
    title: String,
    options: List<String>,
    store: Store<String>? = null,
) {
    menuGroup(styling, baseClass, id, prefix) {
        title(title)
        titleStyle { margins { bottom { smaller } } }
        items {
            radioGroup(items = options, store = store) {
                itemStyle(menuOptionStyle)
            }
        }
    }
}


fun RenderContext.menuDivider() = box(baseClass = menuDividerCss) { }