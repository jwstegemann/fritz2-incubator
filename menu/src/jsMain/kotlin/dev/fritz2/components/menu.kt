package dev.fritz2.components

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.SimpleHandler
import dev.fritz2.binding.Store
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.BasicParams
import dev.fritz2.styling.params.Style
import dev.fritz2.styling.params.plus
import dev.fritz2.styling.params.styled
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons
import dev.fritz2.styling.theme.Theme
import kotlinx.coroutines.flow.flowOf

class MenuComponent {

    companion object {
        private val menuContainerCss: Style<BasicParams> = {
            position { relative { } }
            display { inlineBlock }
        }

        private val menuInnerCss: Style<BasicParams> = {
            position { absolute { } }
            zIndex { "100" }
            radius { "6px" }
            background { color { lightestGray } }

            padding { normal }
            minWidth { "20vw" }

            css(" box-shadow: rgba(0, 0, 0, 0.1) 0px 10px 15px -3px, rgba(0, 0, 0, 0.05) 0px 4px 6px -2px;")
        }
    }

    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)
    internal val visible = ComponentProperty(value = flowOf(false))

    fun render(renderContext: RenderContext) {
        renderContext.apply {
            box(styling = menuContainerCss) {
                visible.value.render {
                    if (it)
                        box(styling = menuInnerCss) {
                            items.value?.invoke(this)
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
    prefix: String = "alert",
    build: MenuComponent.() -> Unit,
): SimpleHandler<Unit> {

    val visibilityStore = object : RootStore<Boolean>(false) {
        val show = handle<Unit> { _, _ -> true }
    }

    // TODO: Pass down styling params
    MenuComponent().apply(build + {
        visible(visibilityStore.data)
    }).render(this)

    return visibilityStore.show
}


class MenuItemComponent {
    val label = ComponentProperty<String?>(value = null)
    val leftIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)
    val rightIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)

    fun render(renderContext: RenderContext) {
        renderContext.apply {
            lineUp {
                items {
                    leftIcon.value?.invoke(Theme().icons)
                    label.value?.let {
                        (::h6.styled {
                            // TODO: Item styling
                        }) { +it }
                    }
                    rightIcon.value?.invoke(Theme().icons)
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
) = MenuItemComponent().apply(build).render(this)


class MenuItemGroupComponent {
    val title = ComponentProperty<String?>(value = null)
    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)

    fun render(renderContext: RenderContext) {
        renderContext.apply {
            stackUp {
                items {
                    title.value?.let {
                        (::h6.styled {
                            // TODO: Group header style
                        }) { +it }
                    }
                    this@MenuItemGroupComponent.items.value?.invoke(this)
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
    build: MenuItemGroupComponent.() -> Unit,
) = MenuItemGroupComponent().apply(build).render(this)

fun RenderContext.menuCheckboxGroup(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "menu-checkbox-group",
    title: String,
    options: List<String>,
    store: Store<List<String>>? = null,
) {
    menuGroup {
        title(title)
        items {
            checkboxGroup(items = options, store = store)
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
    menuGroup {
        title(title)
        items {
            radioGroup(items = options, store = store)
        }
    }
}