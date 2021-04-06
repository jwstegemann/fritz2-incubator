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
        horizontal { normal }
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
 * Additionally, it is also possible to extend the menu-DSL by writing extension methods. See [MenuEntriesContext] for
 * more information.
 * ```
 */
@ComponentMarker
open class MenuComponent : Component<Unit> {

    companion object {
        private val staticContainerCss = staticStyle("menu-container") {
            display { inlineFlex }
            width { minContent }
        }

        private val staticDropdownContainerCss = staticStyle("menu-dropdown-container") {
            position(
                sm = { static },
                md = { relative { } }
            )
        }

        private val staticDropdownCss = staticStyle("menu-dropdown") {
            position(
                sm = { absolute { left { "0px" } } },
                md = { absolute { } }
            )
            width(
                sm = { "100%" },
                md = { minContent }
            )
            zIndex { overlay }

            background { color { neutral } }
            radius { "6px" }
            paddings { vertical { smaller } }
            overflow(
                sm = { hidden },
                md = { visible }
            )

            boxShadow { raised }

            // FIXME: Animation not working
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
    val entries = ComponentProperty<(MenuEntriesContext.() -> Unit)?>(value = null)
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
            box(baseClass = staticContainerCss, styling = placement.containerLayout) {

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
            entries.value?.let {
                val entriesContext = MenuEntriesContext().apply(it)

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
    build: MenuComponent.() -> Unit,
) = MenuComponent()
    .apply(build)
    .render(this, styling, baseClass, id, prefix)


typealias MenuEntry = Component<Unit>

/**
 * Context used to build the entries of the menu.
 *
 * The menu-entry-DSL can be extended via standard Kotlin extension methods. Custom entries must implement the
 * [MenuEntry] interface and are added to the Menu via the [MenuEntriesContext.addEntry] method which is accessibly from
 * within the extension method.
 *
 * The following method adds an instance of `MyMenuEntry` to the Menu. It can simply be called from within the `entries`
 * context of [MenuComponent].
 * Notice that `addEntry` is invoked in the end; the entry wouldn't be added otherwise!
 *
 * ```kotlin
 * fun MenuEntriesContext.example(expression: MyContext.() -> Unit) = MyMenuEntry()
 *      .apply(expression)
 *      .run(::addEntry)
 * ```
 */
open class MenuEntriesContext {

    class ItemContext {
        val icon = ComponentProperty<(Icons.() -> IconDefinition)?>(value = null)
        val text = ComponentProperty("")

        private var enabled = flowOf(true)
        fun enabled(value: Boolean) = enabled(flowOf(value))
        fun enabled(value: Flow<Boolean>) {
            enabled = value
        }

        fun build() = MenuItem(
            icon.value?.invoke((Theme().icons)),
            text.value,
            enabled
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


    private val _entries = mutableListOf<MenuEntry>()
    val entries: List<MenuEntry>
        get() = _entries.toList()

    fun addEntry(entry: MenuEntry) {
        _entries += entry
    }


    fun item(expression: ItemContext.() -> Unit): Flow<MouseEvent> = ItemContext()
        .apply(expression)
        .build()
        .also(::addEntry)
        .run { clicks }

    fun custom(content: RenderContext.() -> Unit) = CustomContentContext()
        .apply { content(content) }
        .build()
        .run(::addEntry)

    fun subheader(expression: SubheaderContext.() -> Unit) = SubheaderContext()
        .apply(expression)
        .build()
        .run(::addEntry)

    fun subheader(text: String) = subheader { text(text) }

    fun divider() = addEntry(MenuDivider())
}


data class MenuItem(
    val icon: IconDefinition?,
    val text: String,
    val enabled: Flow<Boolean>
) : MenuEntry {

    companion object {
        private val staticMenuItemCss = staticStyle("menu-item") {
            width { "100%" }
            alignItems { center }

            radius { "6px" }
        }

        private val menuItemActiveStyle: Style<FlexParams> = {
            hover {
                background { color { gray300 } }
                css("filter: brightness(90%);")
            }
        }

        private val menuItemButtonVariant: Style<BasicParams> = {
            fontWeight { normal }
            color { Theme().fontColor }
            focus {
                boxShadow { none }
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
            enabled.render { enabled ->
                box(
                    baseClass = staticMenuItemCss,
                    styling = if (enabled) menuItemActiveStyle else ({ })
                ) {
                    clickButton() {
                        icon?.let {
                            icon { def(it) }
                        }
                        variant { menuItemButtonVariant }
                        text(text)
                        disabled(!enabled)
                    }.map { it } handledBy clickStore.forwardMouseEvents
                }
            }
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
