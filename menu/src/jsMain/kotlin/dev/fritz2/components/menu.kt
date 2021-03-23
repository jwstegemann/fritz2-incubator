package dev.fritz2.components

import dev.fritz2.binding.RootStore
import dev.fritz2.dom.Window
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.identification.uniqueId
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons
import dev.fritz2.styling.theme.Theme
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.w3c.dom.events.MouseEvent


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
    val dropdownStyle: Style<BoxParams>
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


private val staticMenuEntryCss = staticStyle("menu-entry") {
    width { "100%" }
    paddings {
        horizontal { small }
        vertical { smaller }
    }
    radius { "6px" }
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
            minWidth { minContent }
            zIndex { "100" }

            background { color { neutral } }
            radius { "6px" }
            paddings { vertical { smaller } }

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

    val toggle = ComponentProperty<RenderContext.() -> Unit> {
        pushButton {
            icon { fromTheme { menu } }
            variant { outline }
        }
    }
    val items = ComponentProperty<(MenuEntriesContext.() -> Unit)?>(value = null)
    val placement = ComponentProperty<MenuPlacements.() -> MenuPlacement> { bottom }

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
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
                            renderDropdown(styling, placement, baseClass, id, prefix)
                        } else {
                            box { /* just an empty placeholder */ }
                        }
                    }
                }
            }
        }
    }

    private fun RenderContext.renderDropdown(
        styling: BoxParams.() -> Unit,
        placement: MenuPlacement,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        val uniqueDropdownId = id ?: "menu-dropdown-${uniqueId()}"

        box(
            styling = { this as BoxParams
                styling()
                placement.dropdownStyle()
            },
            baseClass = baseClass + staticDropdownCss,
            id = uniqueDropdownId,
            prefix = prefix
        ) {
            items.value?.let {
                val entriesContext = MenuEntriesContext().apply(it)
                entriesContext.entries.forEach { entry ->
                    entry.render(context = this, styling = {}, StyleClass.None, id = null, prefix)
                }
            }
        }
        listenToWindowEvents(uniqueDropdownId)
    }

    private fun RenderContext.listenToWindowEvents(dropdownId: String) {
        Window.clicks.events
            .drop(1) // filter first event so the dropdown does not get closed imediately
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
    baseClass: StyleClass = StyleClass.None,
    id: String = "menu-dropdown-${uniqueId()}",
    prefix: String = "menu-dropdown",
    build: MenuComponent.() -> Unit,
) = MenuComponent()
    .apply(build)
    .render(this, styling, baseClass, id, prefix)


interface MenuEntry : Component<Unit>

class MenuEntriesContext {

    class ItemContext {
        val leftIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)
        val text = ComponentProperty("")
        val rightIcon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)

        fun build() = MenuItem(
            leftIcon.value?.invoke((Theme().icons)),
            text.value,
            rightIcon.value?.invoke(Theme().icons)
        )
    }

    class CustomContentContext {
        val content = ComponentProperty<RenderContext.() -> Unit> { }
        fun build() = MenuCustomContent(content.value)
    }

    class SubheaderContext {
        val text = ComponentProperty("")
        fun build() = MenuSubheader(text.value)
    }

    class DividerContext {
        fun build() = MenuDivider()
    }


    private val _entries = mutableListOf<MenuEntry>()
    val entries: List<MenuEntry>
        get() = _entries.toList()


    fun item(expression: ItemContext.() -> Unit): Flow<MouseEvent> {
        val item = ItemContext()
            .apply(expression)
            .build()
            .also { _entries += it }

        return item.clicks
    }

    fun custom(content: RenderContext.() -> Unit) = CustomContentContext()
        .apply { content(content) }
        .build()
        .also { _entries += it }

    fun subheader(expression: SubheaderContext.() -> Unit) = SubheaderContext()
        .apply(expression)
        .build()
        .also { _entries += it }

    fun subheader(text: String) = subheader { text(text) }

    fun divider(expression: DividerContext.() -> Unit = { }) = DividerContext()
        .apply(expression)
        .build()
        .also { _entries += it }
}


data class MenuItem(
    val leftIcon: IconDefinition?,
    val text: String,
    val rightIcon: IconDefinition?
) : MenuEntry {

    companion object {
        private val staticMenuItemCss = staticStyle("menu-item") {
            width { "100%" }
            paddings {
                horizontal { small }
                vertical { smaller }
            }
            alignItems { center }

            radius { "6px" }
            hover {
                background { color { gray300 } }
                css("filter: brightness(90%);")
            }
        }
    }


    private val clickStore = object : RootStore<Unit>(Unit) {
        val forwardMouseEvents = handleAndEmit<MouseEvent, MouseEvent> { _, e -> emit(e) }
    }

    val clicks: Flow<MouseEvent>
        get() = clickStore.forwardMouseEvents


    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        context.apply {
            flexBox(
                styling = { this as BoxParams
                    styling()
                },
                baseClass = baseClass + staticMenuEntryCss + staticMenuItemCss,
                id = id,
                prefix = prefix,
            ) {
                leftIcon?.let {
                    icon { def(it) }
                }

                (::label.styled {
                    width { "100%" }
                    margins { horizontal { tiny } }
                    css("white-space: nowrap")
                }) { +text }

                rightIcon?.let {
                    icon { def(it) }
                }
            }.clicks.events handledBy clickStore.forwardMouseEvents
        }
    }
}

data class MenuCustomContent(
    val content: RenderContext.() -> Unit
) : MenuEntry {
    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        context.apply {
            box(
                styling = {
                    this as BoxParams
                    styling()
                },
                baseClass + staticMenuEntryCss,
                id,
                prefix
            ) {
                content(this)
            }
        }
    }
}

data class MenuSubheader(
    val text: String
) : MenuEntry {
    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        context.apply {
            h5(baseClass = staticMenuEntryCss.name) { +text }
        }
    }
}

class MenuDivider : MenuEntry {

    companion object {
        private val staticMenuDividerCss = staticStyle("menu-divider") {
            width { "100%" }
            height { "1px" }
            margins { vertical { smaller } }
            background { color { gray300 } }
        }
    }

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        context.apply {
            box(baseClass = staticMenuDividerCss) { }
        }
    }
}
