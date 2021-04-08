package dev.fritz2.components.datatable

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.Store
import dev.fritz2.binding.SubStore
import dev.fritz2.binding.sub
import dev.fritz2.components.*
import dev.fritz2.dom.html.*
import dev.fritz2.dom.states
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.Lenses
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.name
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.*
import dev.fritz2.styling.whenever
import kotlinx.coroutines.flow.*
import kotlin.collections.Map

// TODO: Remove theme stuff, if code is moved into the fritz2 core project!
//  Add specific interface and implementation into the original fritz2's theme!
interface DataTableStyles {
    val headerColors: ColorScheme
    val columnColors: ColorScheme
    val headerStyle: Style<BasicParams>
    val columnStyle: BasicParams.(value: IndexedValue<Any>) -> Unit
    val sorterStyle: Style<BasicParams>
}

class DataTableTheme : DefaultTheme() {
    override val name = "Theme with Table specific styles"

    val dataTableStyles = object : DataTableStyles {
        override val headerColors: ColorScheme
            get() = colors.primary

        override val columnColors: ColorScheme
            get() = ColorScheme(colors.gray100, colors.gray700, colors.gray300, colors.gray900)

        private val basic: Style<BasicParams> = {
            paddings {
                vertical { smaller }
                left { smaller }
                right { large }
            }
        }

        override val headerStyle: Style<BasicParams>
            get() = {
                background { color { headerColors.base } }
                color { headerColors.baseContrast }
                verticalAlign { middle }
                fontSize { normal }
                position { relative {} }
                basic()
                borders {
                    right {
                        width { "1px" }
                        style { solid }
                        color { headerColors.baseContrast }
                    }
                }
            }

        override val columnStyle: BasicParams.(value: IndexedValue<Any>) -> Unit
            get() = { (index, _) ->
                if (index % 2 == 1) {
                    background { color { columnColors.base } }
                    color { columnColors.baseContrast }
                } else {
                    background { color { columnColors.highlight } }
                    color { columnColors.highlightContrast }
                }
                basic()
                borders {
                    right {
                        width { "1px" }
                        style { solid }
                        color { columnColors.highlight }
                    }
                }
            }

        override val sorterStyle: Style<BasicParams>
            get() = {
                display { flex }
                position {
                    absolute {
                        right { "-1.125rem" }
                        top { "calc(50% -15px)" }
                    }
                }
                css("cursor:pointer;")
            }
    }
}

// TODO: End of provisional Theming stuff

enum class Sorting {
    DISABLED,
    NONE,
    ASC,
    DESC
}

enum class SelectionMode {
    None,
    Single,
    Multi
}

@Lenses
data class Column<T>(
    val id: String, // must be unique!
    val lens: Lens<T, String>? = null,
    val headerName: String = "",
    val minWidth: Property? = null,
    val maxWidth: Property? = null,
    val hidden: Boolean = false,
    val position: Int = 0,
    val sorting: Sorting = Sorting.NONE,
    val sortBy: Comparator<T>? = null,
    val styling: BasicParams.(value: IndexedValue<T>) -> Unit = { _ -> },
    val content: Td.(
        value: IndexedValue<T>,
        cellStore: Store<String>?,
        rowStore: SubStore<List<T>, List<T>, T>
    ) -> Unit,
    val headerStyling: Style<BasicParams> = {},
    val headerContent: Div.(column: Column<T>) -> Unit
)

data class ColumnIdSorting(
    val id: String?,
    val strategy: Sorting = Sorting.NONE
) {
    companion object {
        fun noSorting() = ColumnIdSorting(null)

        fun <T> of(column: Column<T>) = ColumnIdSorting(column.id, column.sorting)
    }
}

typealias SortingPlan = List<ColumnIdSorting>
typealias ColumnSortingPlan<T> = List<Pair<Column<T>, Sorting>>

/*
Sortieren basiert auf drei unabhängigen Komponenten:
- Sortierkonfiguration + Änderung [fehlt noch!] SortingPlanReducer: StateStore hält SortingPlan und berechnet neuen, wenn Column (per Id) aktiviert wird
- Rendern des Sortierbedienelements [SortingRenderer] rendert in Tabellenkopf Sorting Steuerelemente / Anzeigen.
- Durchführung der Sortierung [Sorter<T>] arbeitet nach SortingPlan
 */

interface RowSorter<T> {
    fun sortedBy(
        rows: List<T>,
        columnSortingPlan: ColumnSortingPlan<T>
    ): List<T>
}

class OneColumnSorter<T> : RowSorter<T> {
    override fun sortedBy(
        rows: List<T>,
        columnSortingPlan: ColumnSortingPlan<T>
    ): List<T> =
        if (columnSortingPlan.isNotEmpty()) {
            val (config, sorting) = columnSortingPlan.first()
            if (
                sorting != Sorting.DISABLED
                && sorting != Sorting.NONE
                && (config.lens != null || config.sortBy != null)
            ) {
                rows.sortedWith(
                    createComparator(config, sorting),
                )
            } else rows
        } else {
            rows
        }

    private fun createComparator(
        column: Column<T>,
        sorting: Sorting
    ): Comparator<T> {
        if (column.sortBy == null) {
            return when (sorting) {
                Sorting.ASC -> {
                    compareBy { column.lens!!.get(it) }
                }
                else -> {
                    compareByDescending { column.lens!!.get(it) }
                }
            }
        } else {
            return when (sorting) {
                Sorting.ASC -> {
                    column.sortBy
                }
                else -> {
                    column.sortBy.reversed()
                }
            }
        }
    }
}

interface SortingRenderer {
    fun renderSortingActive(context: Div, sorting: Sorting)
    fun renderSortingLost(context: Div)
    fun renderSortingDisabled(context: Div)
}

class SingleArrowSortingRenderer : SortingRenderer {
    override fun renderSortingActive(context: Div, sorting: Sorting) {
        context.apply {
            when (sorting) {
                Sorting.NONE -> renderSortingLost((this))
                else -> icon { fromTheme { if (sorting == Sorting.ASC) arrowUp else arrowDown } }
            }
        }
    }

    override fun renderSortingLost(context: Div) {
        context.apply {
            icon { fromTheme { sort } }
        }
    }

    override fun renderSortingDisabled(context: Div) {
        // just show nothing in this case
    }
}

interface SortingPlanReducer {
    fun reduce(sortingPlan: SortingPlan, activated: ColumnIdSorting): SortingPlan
}

class TogglingSortingPlanReducer : SortingPlanReducer {
    override fun reduce(sortingPlan: SortingPlan, activated: ColumnIdSorting): SortingPlan =
        if (activated.strategy == Sorting.DISABLED) {
            sortingPlan
        } else {
            listOf(
                ColumnIdSorting(
                    activated.id,
                    if (sortingPlan.isNotEmpty() && sortingPlan.first().id == activated.id) {
                        when (sortingPlan.first().strategy) {
                            Sorting.ASC -> Sorting.DESC
                            Sorting.DESC -> Sorting.NONE
                            else -> Sorting.ASC
                        }
                    } else {
                        Sorting.ASC
                    }
                )
            )
        }
}

/**
 * Central type for dynamic column configuration state
 */
data class State(
    val order: List<String>,
    val sortingPlan: SortingPlan,
) {
    fun <T> orderedColumnsWithSorting(columns: Map<String, Column<T>>):
            List<Pair<Column<T>, ColumnIdSorting>> =
        order.map { colId ->
            val sortingIndexForCurrentColumn = sortingPlan.indexOfFirst { (id, _) -> id == colId }
            if (sortingIndexForCurrentColumn != -1) {
                columns[colId]!! to sortingPlan[sortingIndexForCurrentColumn]
            } else {
                columns[colId]!! to ColumnIdSorting.noSorting()
            }
        }

    fun <T> columnSortingPlan(columns: Map<String, Column<T>>): ColumnSortingPlan<T> =
        sortingPlan.map { (colId, sorting) -> columns[colId]!! to sorting }
}

class StateStore<T, I>(private val sortingPlanReducer: SortingPlanReducer) : RootStore<State>(
    State(emptyList(), emptyList())
) {
    fun renderingData(component: TableComponent<T, I>) =
        data.map { it.orderedColumnsWithSorting(component.columns.value.columns) }

    val sortingChanged = handle { state, activated: ColumnIdSorting ->
        state.copy(sortingPlan = sortingPlanReducer.reduce(state.sortingPlan, activated))
    }

    // TODO: Add handler for ordering / hiding or showing columns (change ``order`` property)
    //  Example for UI for changing: https://tailwindcomponents.com/component/table-ui-with-tailwindcss-and-alpinejs
}


class RowSelectionStore<T, I>(private val rowIdProvider: (T) -> I) : RootStore<List<T>>(emptyList()) {

    val selectedData = data.drop(1)

    val syncHandler = handle<List<T>> { old, allItems ->
        old.mapNotNull { oldItem -> allItems.firstOrNull { rowIdProvider(it) == rowIdProvider(oldItem) } }
    }

    val selectRows = handleAndEmit<T, List<T>> { selectedRows, new ->
        val newSelection = if (selectedRows.contains(new))
            selectedRows - new
        else {
            val temp = selectedRows.map { if (rowIdProvider(it) == rowIdProvider(new)) new else it }
            if (temp.any { rowIdProvider(it) == rowIdProvider(new) }) temp else temp + new
        }
        emit(newSelection)
        newSelection
    }

    val selectRow = handle<T?> { old, new ->
        val newSelection = if (new == null || old.firstOrNull() == new) {
            emptyList()
        } else {
            listOf(new)
        }
        newSelection
    }

    val updateRow = handle<T?> { _, new ->
        if (new == null) emptyList() else listOf(new)
    }

    val dbClickedRow = handleAndEmit<T, T> { selectedRows, new ->
        emit(new)
        selectedRows
    }
}


interface SelectionStrategy<T, I> {
    fun manageSelectionByExtraColumn(component: TableComponent<T, I>)
    fun manageSelectionByRowEvents(
        component: TableComponent<T, I>, rowStore: SubStore<List<T>, List<T>, T>,
        renderContext: Tr
    )
}

class NoSelection<T, I> : SelectionStrategy<T, I> {
    override fun manageSelectionByExtraColumn(component: TableComponent<T, I>) {
        // no extra column needed -> nothing should get selected!
    }

    override fun manageSelectionByRowEvents(
        component: TableComponent<T, I>,
        rowStore: SubStore<List<T>, List<T>, T>,
        renderContext: Tr
    ) {
        // don't wire events -> nothing should get selected!
    }
}

class SelectionByCheckBox<T, I> : SelectionStrategy<T, I> {

    private val commonSettings: TableComponent.TableColumnsContext.TableColumnContext<T>.(
        component: TableComponent<T, I>
    ) -> Unit = { component ->
        width {
            min("60px")
            max("60px")
        }
        sorting { disabled }
        content { _, _, rowStore ->
            checkbox(
                {
                    margin { "0" }
                }, id = uniqueId()
            ) {
                checked(
                    component.selectionStore.data.map { selectedRows ->
                        selectedRows.contains(rowStore.current)
                    }
                )
                events {
                    when (component.selection.value.selectionMode) {
                        SelectionMode.Single ->
                            clicks.events.map { rowStore.current } handledBy component.selectionStore.selectRow
                        SelectionMode.Multi ->
                            clicks.events.map { rowStore.current } handledBy component.selectionStore.selectRows
                        else -> Unit
                    }
                }
            }
        }
    }

    private val headerSettings: TableComponent.TableColumnsContext.TableColumnContext<T>.(
        component: TableComponent<T, I>
    ) -> Unit = { component ->
        header {
            content {
                checkbox({ display { inlineBlock } }, id = uniqueId()) {
                    checked(
                        component.selectionStore.data.map {
                            it.isNotEmpty() && it == component.dataStore.current
                        }
                    )
                    events {
                        changes.states().map { selected ->
                            if (selected) {
                                component.dataStore.current
                            } else {
                                emptyList()
                            }
                        } handledBy component.selectionStore.update
                    }
                }
            }
        }
    }

    override fun manageSelectionByExtraColumn(component: TableComponent<T, I>) {
        with(component) {
            prependAdditionalColumns {
                when (selection.value.selectionMode) {
                    SelectionMode.Multi -> {
                        column {
                            headerSettings(component)
                            commonSettings(component)
                        }
                    }
                    SelectionMode.Single -> {
                        column {
                            commonSettings(component)
                        }
                    }
                    else -> {
                    }
                }
            }
        }
    }

    override fun manageSelectionByRowEvents(
        component: TableComponent<T, I>,
        rowStore: SubStore<List<T>, List<T>, T>,
        renderContext: Tr
    ) {
        // don't wire events -> extra column with checkbox handles everything!
    }
}

class SelectionByClick<T, I> : SelectionStrategy<T, I> {
    override fun manageSelectionByExtraColumn(component: TableComponent<T, I>) {
        // no extra column needed -> selection is handled by clicks!
    }

    override fun manageSelectionByRowEvents(
        component: TableComponent<T, I>,
        rowStore: SubStore<List<T>, List<T>, T>,
        renderContext: Tr
    ) {
        renderContext.apply {
            when (component.selection.value.selectionMode) {
                SelectionMode.Single ->
                    clicks.events.map { rowStore.current } handledBy component.selectionStore.selectRow
                SelectionMode.Multi ->
                    clicks.events.map { rowStore.current } handledBy component.selectionStore.selectRows
                else -> Unit
            }
        }
    }
}

// TODO: Remove if issue #332 is done
//  https://github.com/jwstegemann/fritz2/issues/332
val <T> IndexedValue<T>.odd: Boolean
    get() = index % 2 == 1

val <T> IndexedValue<T>.even: Boolean
    get() = index % 2 == 0


interface IndexBasedStyling<T> {
    fun styled(index: IndexedValue<T>): Style<BoxParams>?
}

fun <T> BoxParams.styledByIndex(index: IndexedValue<T>, expression: () -> IndexBasedStyling<T>) =
    expression().styled(index)?.invoke()

class StaticIndexBasedStyling<T>(private val styling: Style<BoxParams>) : IndexBasedStyling<T> {
    override fun styled(index: IndexedValue<T>): Style<BoxParams> = styling
}

fun <T> always(value: Style<BoxParams>) = StaticIndexBasedStyling<T>(value)

class CyclingIndexBasedStyling<T>(vararg styling: Style<BoxParams>) : IndexBasedStyling<T> {
    private val stylings by lazy {
        styling.toList()
    }

    override fun styled(index: IndexedValue<T>): Style<BoxParams> = stylings[index.index % stylings.size]
}

class OddEvenContext {
    val odd = ComponentProperty<Style<BoxParams>> { }
    val even = ComponentProperty<Style<BoxParams>> { }
}

fun <T> oddEven(value: OddEvenContext.() -> Unit): IndexBasedStyling<T> = OddEvenContext().apply(value).let {
    CyclingIndexBasedStyling(it.even.value, it.odd.value)
}

fun <T> odd(value: Style<BoxParams>): IndexBasedStyling<T> = object : IndexBasedStyling<T> {
    override fun styled(index: IndexedValue<T>): Style<BoxParams>? = if (index.odd) value else null
}

fun <T> even(value: Style<BoxParams>): IndexBasedStyling<T> = object : IndexBasedStyling<T> {
    override fun styled(index: IndexedValue<T>): Style<BoxParams>? = if (index.even) value else null
}

class CyclingContext {
    val strategies = mutableListOf<Style<BoxParams>>()

    fun add(value: Style<BoxParams>) {
        strategies.add(value)
    }
}

fun <T> cycling(value: CyclingContext.() -> Unit): IndexBasedStyling<T> =
    CyclingIndexBasedStyling(*CyclingContext().apply(value).strategies.toTypedArray())

class ExpressionBasedIndexBasedStyling<T>(
    private val expression: (IndexedValue<T>) -> Boolean,
    private val strategy: IndexBasedStyling<T>
) :
    IndexBasedStyling<T> {
    override fun styled(index: IndexedValue<T>): Style<BoxParams>? =
        if (expression(index)) strategy.styled(index) else null
}

fun <T> onlyIf(
    expression: (IndexedValue<T>) -> Boolean,
    strategy: () -> IndexBasedStyling<T>
): IndexBasedStyling<T> =
    ExpressionBasedIndexBasedStyling(expression, strategy())

class HierarchicalIndexBasedStyling<T>(vararg strategy: IndexBasedStyling<T>) : IndexBasedStyling<T> {
    private val strategies by lazy {
        strategy.toList()
    }

    override fun styled(index: IndexedValue<T>): Style<BoxParams>? =
        strategies.mapNotNull { it.styled(index) }.firstOrNull()
}

class HierarchicalContext<T> {
    val strategies = mutableListOf<IndexBasedStyling<T>>()

    fun add(value: () -> IndexBasedStyling<T>?) {
        value()?.let { strategies.add(it) }
    }
}

fun <T> hierarchical(value: HierarchicalContext<T>.() -> Unit): IndexBasedStyling<T> =
    HierarchicalIndexBasedStyling(*HierarchicalContext<T>().apply(value).strategies.toTypedArray())

class CombinedIndexBasedStyling<T>(vararg strategy: IndexBasedStyling<T>) : IndexBasedStyling<T> {
    private val strategies by lazy {
        strategy.toList()
    }

    override fun styled(index: IndexedValue<T>): Style<BoxParams> {
        return {
            strategies.forEach { it.styled(index)?.let { it() } }
        }
    }
}

infix fun <T> IndexBasedStyling<T>.and(other: IndexBasedStyling<T>) =
    CombinedIndexBasedStyling<T>(this, other)

// End issue #332 items

open class TableComponent<T, I>(val dataStore: RootStore<List<T>>, protected val rowIdProvider: (T) -> I) :
    Component<Unit> {
    companion object {
        const val prefix = "table"
        val staticCss = staticStyle(
            prefix,
            """
                display:grid;
                min-width:100%;
                flex: 1;
                border-collapse: collapse;
                text-align: left;
                
                thead,tbody,tr {
                    display:contents;
                }
                             
                thead {             
                  grid-area:main;  
                }
                
                tbody {             
                  grid-area:main;  
                }
                                
                th,
                td {                 
                  &:last-child {
                    border-right: none;
                  }
                }
            """
        )
    }

    private val columnStateIdProvider: (Pair<Column<T>, ColumnIdSorting>) -> String = { it.first.id + it.second }

    // TODO: Cast rausnehmen, sobald Theming in fritz2 verschoben ist!
    private val headerStyle = Theme().unsafeCast<DataTableTheme>().dataTableStyles.headerStyle
    private val columnStyle = Theme().unsafeCast<DataTableTheme>().dataTableStyles.columnStyle
    private val sorterStyle = Theme().unsafeCast<DataTableTheme>().dataTableStyles.sorterStyle


    class TableColumnsContext<T> {

        class TableColumnContext<T> {

            fun build(): Column<T> = Column(
                id.value,
                lens.value,
                header.title.value,
                width?.min?.value,
                width?.max?.value,
                hidden.value,
                position.value,
                sorting.value(SortingContext),
                sortBy.value,
                styling.value,
                content.value,
                header.styling.value,
                header.content.value
            )

            val id = ComponentProperty(uniqueId())
            val lens = ComponentProperty<Lens<T, String>?>(null)

            class WidthContext {
                val min = ComponentProperty<Property?>(null)
                val max = ComponentProperty<Property?>(null)

                fun minmax(value: Property) {
                    max(value)
                    min(value)
                }
            }

            private var width: WidthContext? = WidthContext()

            fun width(expression: WidthContext.() -> Unit) {
                width = WidthContext().apply(expression)
            }

            class HeaderContext<T> {
                val title = ComponentProperty("")
                val styling = ComponentProperty<Style<BasicParams>> {}
                val content = ComponentProperty<Div.(column: Column<T>) -> Unit> { column ->
                    +column.headerName
                }
            }

            private var header: HeaderContext<T> = HeaderContext()

            fun header(styling: Style<BasicParams> = {}, expression: HeaderContext<T>.() -> Unit) {
                header = HeaderContext<T>().apply {
                    expression()
                    styling(styling)
                }
            }

            val hidden = ComponentProperty(false)
            val position = ComponentProperty(0)

            object SortingContext {
                val disabled = Sorting.DISABLED
                val none = Sorting.NONE
                val asc = Sorting.ASC
                val desc = Sorting.DESC
            }

            val sorting = ComponentProperty<SortingContext.() -> Sorting> { none }

            val sortBy = ComponentProperty<Comparator<T>?>(null)
            fun sortBy(expression: (T) -> Comparable<*>) {
                sortBy(compareBy(expression))
            }

            fun sortBy(vararg expressions: (T) -> Comparable<*>) {
                sortBy(compareBy(*expressions))
            }

            val styling = ComponentProperty<BasicParams.(value: IndexedValue<T>) -> Unit> { }

            val content =
                ComponentProperty<Td.(
                    value: IndexedValue<T>,
                    cellStore: Store<String>?,
                    rowStore: SubStore<List<T>, List<T>, T>
                ) -> Unit>
                { _, store, _ -> store?.data?.asText() }
        }

        val columns: MutableMap<String, Column<T>> = mutableMapOf()

        fun column(
            styling: BasicParams.(value: IndexedValue<T>) -> Unit = {},
            expression: TableColumnContext<T>.() -> Unit
        ) {
            TableColumnContext<T>().apply(expression).also {
                it.styling(styling)
            }.build().also {
                columns[it.id] = it
            }
        }

        fun column(
            styling: BasicParams.(value: IndexedValue<T>) -> Unit = {},
            title: String,
            expression: TableColumnContext<T>.() -> Unit
        ) {
            TableColumnContext<T>().apply {
                header { title(title) }
                expression()
            }.also {
                it.styling(styling)
            }.build().also { columns[it.id] = it }
        }

        val styling = ComponentProperty<BoxParams.(IndexedValue<T>) -> Unit> { }
    }

    val columns = ComponentProperty(TableColumnsContext<T>())

    fun columns(styling: BoxParams.(IndexedValue<T>) -> Unit = {}, expression: TableColumnsContext<T>.() -> Unit) {
        columns.value.apply(expression).also {
            it.styling(styling)
        }
    }

    fun prependAdditionalColumns(expression: TableColumnsContext<T>.() -> Unit) {
        val minPos = columns.value.columns.values.minOf { it.position }
        columns.value.columns.putAll(TableColumnsContext<T>().apply(expression).columns
            .mapValues { it.value.copy(position = minPos - 1) }.entries
            .map { (a, b) -> a to b }
            .toMap()
        )
    }

    val selectedRowStyleClass = ComponentProperty(
        staticStyle(
            "selectedRow",
            "td { background-color: ${Theme().colors.secondary.base} !important; }"
        )
    )

    fun selectedRowStyle(value: Style<BasicParams>) {
        selectedRowStyleClass(staticStyle("customSelectedRow") { value() })
    }

    val selectionStore: RowSelectionStore<T, I> = RowSelectionStore(rowIdProvider)

    class EventsContext<T, I>(rowSelectionStore: RowSelectionStore<T, I>) {
        val selectedRows: Flow<List<T>> = rowSelectionStore.selectedData
        val selectedRow: Flow<T?> = rowSelectionStore.selectedData.map { it.firstOrNull() }
        val dbClicks: Flow<T> = rowSelectionStore.dbClickedRow
    }

    fun events(expr: EventsContext<T, I>.() -> Unit) {
        EventsContext(selectionStore).expr()
    }

    class Selection<T, I> {

        object StrategyContext {
            enum class StrategySpecifier {
                Checkbox,
                Click
            }

            val checkbox = StrategySpecifier.Checkbox
            val click = StrategySpecifier.Click
        }

        class Single<T> {
            val store = ComponentProperty<Store<T?>?>(null)
            val row = NullableDynamicComponentProperty<T>(emptyFlow())
        }

        internal var single: Single<T>? = null
        fun single(value: Single<T>.() -> Unit) {
            single = Single<T>().apply(value)
        }

        class Multi<T> {
            val store = ComponentProperty<Store<List<T>>?>(null)
            val rows = DynamicComponentProperty<List<T>>(emptyFlow())
        }

        internal var multi: Multi<T>? = null
        fun multi(value: Multi<T>.() -> Unit) {
            multi = Multi<T>().apply(value)
        }

        val strategy = ComponentProperty<SelectionStrategy<T, I>?>(null)
        fun strategy(expression: StrategyContext.() -> StrategyContext.StrategySpecifier) {
            when (expression(StrategyContext)) {
                StrategyContext.StrategySpecifier.Checkbox -> strategy(SelectionByCheckBox())
                StrategyContext.StrategySpecifier.Click -> strategy(SelectionByClick())
            }
        }

        val selectionMode by lazy {
            when {
                single != null -> {
                    SelectionMode.Single
                }
                multi != null -> {
                    SelectionMode.Multi
                }
                else -> {
                    SelectionMode.None
                }
            }
        }
    }

    val selection = ComponentProperty(Selection<T, I>())
    fun selection(value: Selection<T, I>.() -> Unit) {
        selection.value.value()
        if (selection.value.strategy.value == null) {
            when (selection.value.selectionMode) {
                SelectionMode.Single -> selection.value.strategy(SelectionByClick())
                SelectionMode.Multi -> selection.value.strategy(SelectionByCheckBox())
                else -> selection.value.strategy(NoSelection())
            }
        }
    }

    class Options<T> {

        class Sorting<T> {
            val reducer = ComponentProperty<SortingPlanReducer>(TogglingSortingPlanReducer())
            val sorter = ComponentProperty<RowSorter<T>>(OneColumnSorter())
            val renderer = ComponentProperty<SortingRenderer>(SingleArrowSortingRenderer())
        }

        val sorting = ComponentProperty<Sorting<T>>(Sorting())
        fun sorting(value: Sorting<T>.() -> Unit) {
            sorting.value.apply { value() }
        }

        val width = ComponentProperty<Property?>("100%")
        val maxWidth = ComponentProperty<Property?>(null)
        val height = ComponentProperty<Property?>(null)
        val maxHeight = ComponentProperty<Property?>("97vh")
        val cellMinWidth = ComponentProperty<Property>("130px")
        val cellMaxWidth = ComponentProperty<Property>("1fr")
    }

    val options = ComponentProperty(Options<T>())
    fun options(value: Options<T>.() -> Unit) {
        options.value.apply { value() }
    }

    class HeaderContext {
        val fixedHeader = ComponentProperty(true)
        val fixedHeaderHeight = ComponentProperty<Property>("37px")
        val styling = ComponentProperty<Style<BasicParams>> { }
    }

    val header = ComponentProperty(HeaderContext())
    fun header(styling: Style<BasicParams> = {}, expression: HeaderContext.() -> Unit) {
        header.value.apply {
            expression()
            styling(styling)
        }
    }

    private val stateStore: StateStore<T, I> by lazy {
        StateStore(options.value.sorting.value.reducer.value)
    }

    override fun render(
        context: RenderContext,
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String
    ) {
        selection.value.strategy.value?.manageSelectionByExtraColumn(this)

        stateStore.update(
            State(
                columns.value.columns.values.filter { !it.hidden }.sortedBy { it.position }.map { it.id },
                emptyList()
            )
        )

        context.apply {
            dataStore.data handledBy selectionStore.syncHandler

            // preset selection via external store or flow
            when (selection.value.selectionMode) {
                SelectionMode.Single ->
                    (selection.value.single?.store?.value?.data
                        ?: selection.value.single?.row!!.values) handledBy selectionStore.updateRow
                SelectionMode.Multi -> (selection.value.multi?.store?.value?.data
                    ?: selection.value.multi?.rows!!.values) handledBy selectionStore.update
                else -> Unit
            }

            (::div.styled {
                styling()
                options.value.width.value?.also { width { it } }
                options.value.height.value?.also { height { it } }
                options.value.maxHeight.value?.also { maxHeight { it } }
                options.value.maxWidth.value?.also { maxWidth { it } }
                if (options.value.height.value != null || options.value.width.value != null) {
                    overflow { OverflowValues.auto }
                }
                css("overscroll-behavior: contain")
                position { relative { } }
            }) {
                renderTable(baseClass, id, prefix, rowIdProvider, this)
            }

            // tie selection to external store if needed
            when (selection.value.selectionMode) {
                SelectionMode.Single -> events {
                    selection.value.single!!.store.value?.let { selectedRow handledBy it.update }
                }
                SelectionMode.Multi -> events {
                    selection.value.multi!!.store.value?.let { selectedRows handledBy it.update }
                }
                else -> Unit
            }
        }
    }

    private fun <I> renderTable(
        baseClass: StyleClass?,
        id: String?,
        prefix: String,
        rowIdProvider: (T) -> I,
        RenderContext: RenderContext,
    ) {
        val component = this
        val tableBaseClass = if (baseClass != null) {
            baseClass + staticCss
        } else {
            staticCss
        }

        val gridCols = component.stateStore.data
            .map { (order, _) ->
                var minmax = ""
                //var header = ""
                //var footer = ""
                //var main = ""

                order.forEach { itemId ->
                    component.columns.value.columns[itemId]?.let {
                        val min = it.minWidth ?: component.options.value.cellMinWidth.value
                        val max = it.maxWidth ?: component.options.value.cellMaxWidth.value
                        minmax += "\n" + if (min == max) {
                            max
                        } else {
                            if (!min.contains(Regex("px|%"))) {
                                "minmax(${component.options.value.cellMinWidth.value}, $max)"
                            } else {
                                "minmax($min, $max)"
                            }
                        }
                    }
                }

                """
                    grid-template-columns: $minmax;                
                    grid-template-rows: auto;
                   """
            }


        if (component.header.value.fixedHeader.value) {
            renderFixedHeaderTable(
                rowIdProvider,
                gridCols,
                tableBaseClass,
                id,
                prefix,
                RenderContext
            )
        } else {
            renderSimpleTable(
                rowIdProvider,
                gridCols,
                tableBaseClass,
                id,
                prefix,
                RenderContext
            )
        }
    }

    private fun <I> renderFixedHeaderTable(
        rowIdProvider: (T) -> I,
        gridCols: Flow<String>,
        baseClass: StyleClass,
        id: String?,
        prefix: String,
        RenderContext: RenderContext
    ) {
        val component = this
        RenderContext.apply {
            (::table.styled({
                height { component.header.value.fixedHeaderHeight.value }
                overflow { OverflowValues.hidden }
                position {
                    sticky {
                        top { "0" }
                    }
                }
                zIndex { "1" }
            }, baseClass, "$id-fixedHeader", "$prefix-fixedHeader") {}){
                attr("style", gridCols)
                renderTHead({}, this)
                renderTBody({
                    css("visibility:hidden")
                }, rowIdProvider, this)
            }

            (::table.styled({
                margins {
                    top { "-${component.header.value.fixedHeaderHeight.value}" }
                }
                height { "fit-content" }
            }, baseClass, id, prefix) {}){
                attr("style", gridCols)
                renderTHead({
                    css("visibility:hidden")
                }, this)
                renderTBody({}, rowIdProvider, this)
            }
        }
    }

    private fun <I> renderSimpleTable(
        rowIdProvider: (T) -> I,
        gridCols: Flow<String>,
        baseClass: StyleClass,
        id: String?,
        prefix: String,
        RenderContext: RenderContext
    ) {
        RenderContext.apply {
            (::table.styled({ }, baseClass, id, prefix) {}){
                attr("style", gridCols)
                renderTHead({}, this)
                renderTBody({}, rowIdProvider, this)
            }
        }
    }

    private fun renderTHead(
        styling: GridParams.() -> Unit,
        renderContext: RenderContext
    ) {
        val component = this
        renderContext.apply {
            (::thead.styled() {
                styling()
            }) {
                tr {
                    component.stateStore.renderingData(component)
                        .renderEach(component.columnStateIdProvider) { (column, sorting) ->
                            (::th.styled(column.headerStyling) {
                                headerStyle()
                                component.header.value.styling.value()
                            })  {
                                flexBox({
                                    height { "100%" }
                                    position { relative { } }
                                    alignItems { center }
                                }) {
                                    // Column Header Content
                                    with(column) { headerContent(this) }

                                    // Sorting
                                    (::div.styled(sorterStyle) {}){
                                        if (column.id == sorting.id) {
                                            component.options.value.sorting.value.renderer.value.renderSortingActive(
                                                this,
                                                sorting.strategy
                                            )
                                        } else if (column.sorting != Sorting.DISABLED) {
                                            component.options.value.sorting.value.renderer.value.renderSortingLost(this)
                                        } else {
                                            component.options.value.sorting.value.renderer.value.renderSortingDisabled(
                                                this
                                            )
                                        }
                                        clicks.events.map {
                                            ColumnIdSorting.of(column)
                                        } handledBy component.stateStore.sortingChanged
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private fun <I> renderTBody(
        styling: GridParams.() -> Unit,
        rowIdProvider: (T) -> I,
        renderContext: RenderContext
    ) {
        val component = this
        renderContext.apply {
            (::tbody.styled {
                styling()
            }) {
                component.dataStore.data.combine(component.stateStore.data) { data, state ->
                    component.options.value.sorting.value.sorter.value.sortedBy(
                        data,
                        state.columnSortingPlan(component.columns.value.columns)
                    ).withIndex().toList()
                }.renderEach(IndexedValue<T>::hashCode) { (index, rowData) ->
                    val rowStore = component.dataStore.sub(rowData, rowIdProvider)
                    val selected = component.selectionStore.data.map { selectedRows ->
                        selectedRows.contains(rowStore.current)
                    }
                    tr {
                        className(component.selectedRowStyleClass.value.whenever(selected).name)
                        selection.value.strategy.value?.manageSelectionByRowEvents(component, rowStore, this)
                        dblclicks.events.map { rowStore.current } handledBy component.selectionStore.dbClickedRow
                        component.stateStore.data.map { state -> state.order.mapNotNull { component.columns.value.columns[it] } }
                            .renderEach { column ->
                                (::td.styled {
                                    columnStyle(this, IndexedValue(index, rowData as Any))
                                    columns.value.styling.value(this, IndexedValue(index, rowData))
                                    column.styling(this, IndexedValue(index, rowData))
                                }) {
                                    column.content(
                                        this,
                                        IndexedValue(index, rowData),
                                        if (column.lens != null) rowStore.sub(column.lens) else null,
                                        rowStore,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
