package dev.fritz2.components

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.SimpleHandler
import dev.fritz2.binding.Store
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons


private val menuItemCss = staticStyle("menu-item") {
    margins {
        bottom { normal }
    }
}

private val menuOptionCss: Style<BasicParams> = {
    margins {
        top { smaller }
        bottom { smaller }
    }
}


class MenuComponent {

    companion object {
        private val menuContainerStyle: Style<BasicParams> = {
            position { relative { } }
        }

        private val menuInnerStyle: Style<BasicParams> = {
            position { absolute { } }
            zIndex { "100" }
            radius { "6px" }
            background { color { lightestGray } }

            padding { small }
            minWidth { "20vw" }

            // TODO: Use builtin box-shadow property
            css(" box-shadow: rgba(0, 0, 0, 0.1) 0px 10px 15px -3px, rgba(0, 0, 0, 0.05) 0px 4px 6px -2px;")
        }

        class VisibilityStore : RootStore<Boolean>(false) {
            val show = handle<Unit> { _, _ -> true }
            val dismiss = handle<Unit> { _, _ -> false }
        }
    }

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
            box(styling = menuContainerStyle, prefix = "menu-container") {
                visibilityStore.data.render {
                    if (it)
                        // TODO: Use base class
                        box(
                            styling = menuInnerStyle + styling,
                            baseClass = baseClass,
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
                        box { }
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
): SimpleHandler<Unit> {

    val component = MenuComponent().apply(build)
    component.render(styling, baseClass, id, prefix, this)

    // TODO: Close menu when clicking outside

    return component.visibilityStore.show
}


class MenuItemComponent {
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
                styling = styling + {
                    alignItems { center }
                },
                baseClass = menuItemCss + baseClass,
                id = id,
                prefix = prefix,
            ) {
                leftIcon.value?.let {
                    icon { fromTheme(it) }
                }
                label.value?.let {
                    (::label.styled {
                        margins { left { small } }
                    }) { +it }
                }
                rightIcon.value?.let {
                    icon { fromTheme(it) }
                }
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
) = MenuItemComponent()
    .apply(build)
    .render(styling, baseClass, id, prefix, this)


fun RenderContext.menuDivider() {
    box(baseClass = menuItemCss, styling = {
        width { "100%" }
        height { "1px" }
        background { color { lighterGray } }
    }) { }
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
                baseClass = menuItemCss + baseClass,
                id = id,
                prefix = prefix
            ) {
                spacing { none }
                items {
                    title.value?.let {
                        (::h6.styled(styling = titleStyle.value, baseClass = menuItemCss)) { +it }
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
                itemStyle(menuOptionCss)
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
                itemStyle(menuOptionCss)
            }
        }
    }
}