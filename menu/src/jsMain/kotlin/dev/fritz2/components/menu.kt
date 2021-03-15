package dev.fritz2.components

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.Store
import dev.fritz2.dom.DomListener
import dev.fritz2.dom.Window
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.identification.uniqueId
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons
import kotlinx.browser.document
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.MouseEvent


private val staticMenuEntryCss = staticStyle("menu-item") {
    width { "100%" }
    paddings {
        horizontal { small }
        vertical { smaller }
    }
    radius { "6px" }
}

private val staticMenuDividerCss = staticStyle("menu-divider") {
    width { "100%" }
    height { "1px" }
    margins { vertical { smaller } }
    background { color { lighterGray } }
}

private val menuOptionStyle: Style<BasicParams> = {
    margins { vertical { smaller } }
}


// TODO: Move styles into theme

interface MenuStyles {
    val placements: MenuPlacements
}

interface MenuPlacements {
    val left: MenuPlacement
    val right: MenuPlacement
    val bottom: MenuPlacement
}

interface MenuPlacement {
    val containerLayout: Style<FlexParams>
    val dropdownStyle: Style<BasicParams>
}

val menuStyles = object : MenuStyles {

    override val placements = object : MenuPlacements {
        override val left = object : MenuPlacement {
            override val containerLayout: Style<FlexParams> = {
                direction { rowReverse }
            }
            override val dropdownStyle: Style<BasicParams> = {
                css("transform: translateX(-100%)")
            }
        }
        override val right = object : MenuPlacement {
            override val containerLayout: Style<FlexParams> = {
                direction { row }
            }
            override val dropdownStyle: Style<BasicParams> = {
                // No special styles needed
            }
        }
        override val bottom = object : MenuPlacement {
            override val containerLayout: Style<FlexParams> = {
                direction { column }
            }
            override val dropdownStyle: Style<BasicParams> = {
                // No special styles needed
            }
        }
    }
}


@ComponentMarker
open class MenuComponent : Component<Unit> {

    companion object {
        private val staticContainerCss = staticStyle("menu-container") {
            width { minContent }
        }

        private val staticDropdownContainerCss = staticStyle("menu-dropdown-container") {
            position { relative { } }
        }

        private val staticDropdownCss = staticStyle("menu-dropdown") {
            position { absolute { } }
            zIndex { "100" }
            radius { "6px" }
            background { color { base } }

            minWidth { minContent }

            boxShadow { raised }

            // FIXME: Animation not working
            // fade-in-animation
            //opacity { "1" }
            //css("transition: opacity 1s ease-in-out;")
        }
    }

    private val visibilityStore = object : RootStore<Boolean>(false) {
        val show = handle<Unit> { _, _ -> true }
        val dismiss = handle<Unit> { _, _ -> false }
    }

    val toggle = ComponentProperty<RenderContext.() -> Unit> { }
    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)
    val placement = ComponentProperty<MenuPlacements.() -> MenuPlacement> { bottom }

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String
    ) {
        val uniqueId = "menu-dropdown-${uniqueId()}"
        val placement = placement.value.invoke(menuStyles.placements)

        context.apply {
            flexBox(baseClass = staticContainerCss, styling = placement.containerLayout) {

                box(id = "menu-toggle-${uniqueId()}") {
                    toggle.value(this)
                    clicks.events.map { } handledBy visibilityStore.show
                }

                box(baseClass = staticDropdownContainerCss) {
                    visibilityStore.data.render { visible ->
                        if (visible) {
                            box(
                                // TODO: Fix styling
                                styling = /*styling + */placement.dropdownStyle,
                                baseClass = baseClass?.let { it + staticDropdownCss } ?: staticDropdownCss,
                                id = uniqueId,
                                prefix = prefix
                            ) {
                                items.value?.invoke(this)
                            }
                            listenToWindowEvents(uniqueId)
                        } else {
                            box { /* just an empty placeholder */ }
                        }
                    }
                }
            }
        }
    }

    private fun RenderContext.listenToWindowEvents(dropdownId: String) {
        Window.clicks.events
            .filter { event ->
                val dropdownElement = document.getElementById(dropdownId)
                dropdownElement?.let {
                    val bounds = it.getBoundingClientRect()
                    // Only handle clicks outside of the menu dropdown
                    return@filter !(event.x >= bounds.left
                            && event.x <= bounds.right
                            && event.y >= bounds.top
                            && event.y <= bounds.bottom)
                }
                false
            }
            .map { } handledBy visibilityStore.dismiss
    }
}

fun RenderContext.menu(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String = "menu-dropdown-${uniqueId()}",
    prefix: String = "menu-dropdown",
    build: MenuComponent.() -> Unit,
) = MenuComponent()
    .apply(build)
    .render(this, styling, baseClass, id, prefix)


@ComponentMarker
open class MenuItemComponent : Component<Unit>, EventProperties<HTMLDivElement> by EventMixin() {

    companion object {
        private val menuItemStyle: Style<FlexParams> = {
            alignItems { center }
            hover {
                background { color { lightestGray } }
                css("filter: brightness(90%);")
            }
        }
    }

    val label = ComponentProperty<String?>(value = null)
    val leftIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)
    val rightIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String
    ) {
        context.apply {
            flexBox(
                // TODO: Fix styling
                styling = /*styling + */menuItemStyle,
                baseClass = baseClass?.let { it + staticMenuEntryCss } ?: staticMenuEntryCss,
                id = id,
                prefix = prefix,
            ) {
                leftIcon.value?.let {
                    icon { fromTheme(it) }
                }
                label.value?.let {
                    (::label.styled {
                        width { "100%" }
                        margins { horizontal { tiny } }
                        css("white-space: nowrap")
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

    component.render(this, styling, baseClass, id, prefix)
    return clickListener!!
}


@ComponentMarker
open class MenuGroupComponent : Component<Unit> {
    val title = ComponentProperty<String?>(value = null)
    val items = ComponentProperty<(RenderContext.() -> Unit)?>(value = null)

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass?,
        id: String?,
        prefix: String
    ) {
        context.apply {
            stackUp(
                // TODO: Fix styling
                //styling = styling,
                baseClass = baseClass,
                id = id,
                prefix = prefix
            ) {
                spacing { none }
                items {
                    this@MenuGroupComponent.title.value?.let {
                        h5(baseClass = staticMenuEntryCss.name) { +it }
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
    .render(this, styling, baseClass, id, prefix)

fun RenderContext.checkboxMenuGroup(
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
        items {
            checkboxGroup(
                baseClass = staticMenuEntryCss,
                styling = {
                    paddings { top { none } }
                },
                items = options,
                store = store
            ) {
                itemStyle(menuOptionStyle)
            }
        }
    }
}

fun RenderContext.radioMenuGroup(
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
        items {
            radioGroup(
                baseClass = staticMenuEntryCss,
                styling = {
                    paddings { top { none } }
                },
                items = options,
                store = store
            ) {
                itemStyle(menuOptionStyle)
            }
        }
    }
}


fun RenderContext.menuDivider() = box(baseClass = staticMenuDividerCss) { }