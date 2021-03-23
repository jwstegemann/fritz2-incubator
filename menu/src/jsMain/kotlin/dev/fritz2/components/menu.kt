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
import kotlinx.coroutines.flow.*
import org.w3c.dom.events.MouseEvent
import kotlin.js.Date


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


/**
 * This class combines the _configuration_ and the core rendering of a menu.
 *
 * A menu consists of a toggle element as well as it's actual content in form of a drop-down with customizable entries.
 * The dropdown floats around the toggle-element and can be closed by simply clicking outside the dropdown.
 *
 * The toggle-element can be any component and is passed via the `toggle` property. A button with a standard
 * menu-icon is used if no toggle-element is specified.
 *
 * The dropdown can be placed _to the left_, _to the right_ or _below_ the toggle-element. This can be specified via the
 * `placement` property. The default placement is below the toggle.
 *
 * Menu entries are specified via a dedicated context exposed by the `items` property.
 * By default the following types of entries can be added to the menu:
 * - Items
 * - Subheaders
 * - Dividers
 *
 * It is also possible to add any other fritz2 component. In this case all menu-specific styling (such as paddings) has
 * to be done manually, however.
 *
 * Example usage:
 * ```kotlin
 * menu {
 *      toggle { pushButton { text("Toggle") } }
 *      placement { below }
 *      items {
 *          item {
 *              leftIcon { add }
 *              text("Item")
 *          }
 *          divider()
 *          subheader("A subsection starts here")
 *          custom {
 *              // custom content
 *              spinner { }
 *          }
 *      }
 * }
 * ```
 *
 * Additionally, it is also possible to extend the menu-DSL by injecting a custom subclass of [MenuEntriesContext] that
 * adds additional functionality which may be useful for specific use-cases in which a component is used so often that
 * it might be cumbersome to always pass it via the `custom`-property.
 * This is done via the `entriesContextProvider`-parameter of the [menu]-function. The latter is overloaded in such a
 * way that the custom DSL-elements will be available in the `items`-context once passed.
 *
 * Example:
 * ```kotlin
 * // custom MenuEntryContext that adds support for radio-groups:
 * class MyCustomEntriesContext : MenuEntriesContext() {
 *
 *      class RadioGroupContext {
 *            val items = ComponentProperty(listOf<String>())
 *
 *            fun build() = object : MenuEntry {
 *               override fun render(
 *                   context: RenderContext,
 *                   styling: BoxParams.() -> Unit,
 *                   baseClass: StyleClass,
 *                   id: String?,
 *                   prefix: String
 *               ) {
 *                   context.apply {
 *                        radioGroup(items = items.value)
 *                    }
 *                }
 *            }
 *      }
 *
 *      fun radios(expression: RadioGroupContext.() -> Unit) = RadioGroupContext()
 *          .apply(expression)
 *          .build()
 *          .also(::addEntry) // <-- add the entry to the menu
 * }
 *
 * // passing the custom context at the creation of the menu:
 * menu(entriesContextProvider = { MyCustomEntriesContext() }) {
 *      items {
 *          // now available:
 *          radios { ... }
 *      }
 * }
 * ```
 */
@ComponentMarker
open class MenuComponent<E : MenuEntriesContext>(private val entriesContextProvider: () -> E) : Component<Unit> {

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
    val items = ComponentProperty<(E.() -> Unit)?>(value = null)
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
                val entriesContext = entriesContextProvider().apply(it)
                entriesContext.entries.forEach { entry ->
                    entry.render(context = this, styling = {}, StyleClass.None, id = null, prefix)
                }
            }
        }
        listenToWindowEvents(uniqueDropdownId)
    }

    private fun RenderContext.listenToWindowEvents(dropdownId: String) {
        // delay listening so the dropdown is not closed immediately:
        val startListeningMillis = Date.now() + 200

        Window.clicks.events
            .filter { event ->
                if (Date.now() < startListeningMillis)
                    return@filter false

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

/**
 * Creates a standard menu.
 * For a menu with a custom, extended DSL use the overloaded variant of this function.
 *
 * @param styling a lambda expression for declaring the styling as fritz2's styling DSL
 * @param baseClass optional CSS class that should be applied to the element
 * @param id the ID of the element
 * @param prefix the prefix for the generated CSS class resulting in the form ``$prefix-$hash``
 * @param build a lambda expression for setting up the component itself.
 */
fun RenderContext.menu(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass = StyleClass.None,
    id: String = "menu-dropdown-${uniqueId()}",
    prefix: String = "menu-dropdown",
    build: MenuComponent<MenuEntriesContext>.() -> Unit,
) = menu(styling, entriesContextProvider = { MenuEntriesContext() }, baseClass, id, prefix, build)

/**
 * Creates a menu with an explicitly specified [MenuEntriesContext] (-subclass) that can be used to extend the menu-DSL.
 *
 * Important: Make sure to pass a [entriesContextProvider] that _does not_ re-use instances of the custom
 * entries-context but instead returns a new instance every time it is called.
 * The menu will be rendered multiple times otherwise!
 *
 * @param styling a lambda expression for declaring the styling as fritz2's styling DSL
 * @param E the actual type of the custom [MenuEntriesContext] subclass
 * @param entriesContextProvider a lambda returning new instances of the custom entries-context
 * @param baseClass optional CSS class that should be applied to the element
 * @param id the ID of the element
 * @param prefix the prefix for the generated CSS class resulting in the form ``$prefix-$hash``
 * @param build a lambda expression for setting up the component itself.
 */
fun <E : MenuEntriesContext> RenderContext.menu(
    styling: BasicParams.() -> Unit = {},
    entriesContextProvider: () -> E,
    baseClass: StyleClass = StyleClass.None,
    id: String = "menu-dropdown-${uniqueId()}",
    prefix: String = "menu-dropdown",
    build: MenuComponent<E>.() -> Unit,
) = MenuComponent(entriesContextProvider)
    .apply(build)
    .render(this, styling, baseClass, id, prefix)


typealias MenuEntry = Component<Unit>

/**
 * Context used to build the entries of the menu.
 * This class can also be subclassed to extend the menu-entries-DSL as explained in [MenuComponent].
 */
open class MenuEntriesContext {

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

    protected fun addEntry(entry: MenuEntry) {
        _entries += entry
    }


    fun item(expression: ItemContext.() -> Unit): Flow<MouseEvent> {
        val item = ItemContext()
            .apply(expression)
            .build()
            .also(::addEntry)

        return item.clicks
    }

    fun custom(content: RenderContext.() -> Unit) = CustomContentContext()
        .apply { content(content) }
        .build()
        .also(::addEntry)

    fun subheader(expression: SubheaderContext.() -> Unit) = SubheaderContext()
        .apply(expression)
        .build()
        .also(::addEntry)

    fun subheader(text: String) = subheader { text(text) }

    fun divider(expression: DividerContext.() -> Unit = { }) = DividerContext()
        .apply(expression)
        .build()
        .also(::addEntry)
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
            (::h5.styled(baseClass = staticMenuEntryCss) {
                css("white-space: nowrap")
            }) { +text }
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
