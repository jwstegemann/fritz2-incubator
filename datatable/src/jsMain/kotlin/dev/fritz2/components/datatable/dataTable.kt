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
import dev.fritz2.styling.style
import dev.fritz2.styling.theme.*
import kotlinx.coroutines.flow.*
import kotlin.collections.Map
import kotlin.math.abs

// TODO: Remove theme stuff, if code is moved into the fritz2 core project!
//  Add specific interface and implementation into the original fritz2's theme!
//  We need a solution for extending the theme without moving the table specific parts into the styling core!
//  Some types (``Sorting`` and ``StatefulItem`` are unknown there and also should not get exposed there)
interface DataTableStyles {
    val headerStyle: BasicParams.(sorting: Sorting) -> Unit
    val columnStyle: BasicParams.(value: IndexedValue<StatefulItem<Any>>) -> Unit
    val hoveringStyle: BasicParams.(value: IndexedValue<StatefulItem<Any>>) -> Unit
    val sorterStyle: Style<BasicParams>
}

class DataTableTheme : DefaultTheme() {
    override val name = "Theme with Table specific styles"

    val dataTableStyles = object : DataTableStyles {
        val headerColors: ColorScheme
            get() = colors.primary

        /**
         * Semantic: One [ColorScheme] per row:
         *  - base: background of the cell
         *  - baseContrast: text color of the cell
         *  - highlight: background of the cell if row is selected
         *  - highlightContrast: text color of the cell if row is selected
         *
         *  Use cases:
         *   - alternating (odd - even) rows
         *   - grouping for value based categories (for example different ranges applied for visual analyzing)
         *
         *   Therefore a [List] fits best: Very small overhead, but clear semantics and arbitrary coloring is possible.
         */
        val columnColors: List<ColorScheme>
            get() = listOf(
                ColorScheme(
                    colors.gray100,
                    colors.gray700,
                    colors.gray700,
                    colors.gray100
                ),
                ColorScheme(
                    colors.gray300,
                    colors.gray900,
                    colors.gray900,
                    colors.gray300
                )
            )

        val selectionColor: ColorScheme = colors.secondary

        private val basic: Style<BasicParams> = {
            paddings {
                vertical { smaller }
                left { smaller }
                right { large }
            }
        }

        override val headerStyle: BasicParams.(sorting: Sorting) -> Unit
            get() = { sorting ->
                if (sorting == Sorting.ASC || sorting == Sorting.DESC) {
                    background { color { headerColors.highlight } }
                    color { headerColors.highlightContrast }
                } else {
                    background { color { headerColors.base } }
                    color { headerColors.baseContrast }
                }
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

        override val columnStyle: BasicParams.(value: IndexedValue<StatefulItem<Any>>) -> Unit
            get() = { (index, item) ->
                basic()
                with((index + 1) % 2) {
                    if (item.selected) {
                        background { color { selectionColor.base } }
                        color { selectionColor.baseContrast }
                    } else {
                        background { color { columnColors[this@with].base } }
                        color { columnColors[this@with].baseContrast }
                    }
                    borders {
                        right {
                            width { "1px" }
                            style { solid }
                            color { columnColors[abs(this@with - 1)].base }
                        }
                    }
                }
                if (item.sorting == Sorting.ASC || item.sorting == Sorting.DESC) {
                    borders {
                        right {
                            color { headerColors.base }
                            width { normal }
                            style { solid }
                        }
                        left {
                            color { headerColors.base }
                            width { normal }
                            style { solid }
                        }
                    }
                }
            }

        override val hoveringStyle: BasicParams.(value: IndexedValue<StatefulItem<Any>>) -> Unit
            get() = { (index, item) ->
                with((index + 1) % 2) {
                    if (item.selected) {
                        background { color { selectionColor.highlight } }
                        color { selectionColor.highlightContrast }
                    } else {
                        background { color { columnColors[this@with].highlight } }
                        color { columnColors[this@with].highlightContrast }
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

/**
 * Representation of the different sorting status.
 */
enum class Sorting {
    DISABLED,
    NONE,
    ASC,
    DESC
}

/**
 * Representation of the selection possibilities.
 */
enum class SelectionMode {
    None,
    Single,
    Multi
}

/**
 * This class is meant for combining the data of one row with the current state specific properties like the
 * sorting strategy or whether the row is currently selected.
 */
// TODO: Quasi entfernen; Parameter einzeln reinreichen.
//  sorting von enum auf Boolean umstellen -> ASC/Desc = True, sonst False
data class StatefulItem<T>(val item: T, val selected: Boolean, val sorting: Sorting)

/**
 * Main class to define the representation of the data class ``T`` of one table column.
 * This class mainly is the result of the DSL configuration of the datatable.
 *
 * Besides the main properties like the header title string or the [Lens] to grab the current [String] representation
 * of the row data during the rendering process, one can also specify the sorting algorithm to fit the specific
 * type (like a Date for example) or the appearance of the cell styling and content or the header styling and content.
 */
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
    val styling: BasicParams.(value: IndexedValue<StatefulItem<T>>) -> Unit = { _ -> },
    val content: Td.(
        value: IndexedValue<StatefulItem<T>>,
        cellStore: Store<String>?,
        rowStore: SubStore<List<T>, List<T>, T>
    ) -> Unit,
    val headerStyling: Style<BasicParams> = {},
    val headerContent: Div.(column: Column<T>) -> Unit
)

/**
 * Wrapping class to group the id of a [Column] with the sorting strategy.
 * This is the base for the type ``T`` independent [SortingPlan] which itself is necessary for the *internal*
 * [State].
 */
data class ColumnIdSorting(
    val id: String?,
    val strategy: Sorting = Sorting.NONE
) {
    companion object {
        fun noSorting() = ColumnIdSorting(null)

        fun <T> of(column: Column<T>) = ColumnIdSorting(column.id, column.sorting)
    }
}

/**
 * This alias expresses the sorting plan, which is the *ordered* list of column ids paired with their strategies.
 * This is the base concept for the sorting held within a [State] object and used for the [SortingRenderer]
 * in order to create a new plan based upon user interaction (change the direction, change the column to be sorted)
 *
 * There are three different interfaces on that the separate sorting functionalities are implemented:
 *  1. [SortingPlanReducer]: create a plan of columns to be sorted in a specific direction based upon the former
 *                           plan and the triggering user action
 *  2. [RowSorter]: do the actual sorting work based upon the above plan (slightly transformed to typed column
 *                  instead of plain ids)
 *  3. [SortingRenderer]: render the appropriate UI for the sorting actions in the header columns
 *
 * Here is the big picture visualized:
 * ```
 *                          +-----------------------------------------------------------------+
 *                          |                                   (emits new state)             |
 *                          |                                                                 |
 *                          v                                                                 |
 *    +------------------------------------------+                                            |
 *    |                  State                   |                                            |
 *    +------------------------------------------+                                            |
 *    | val sortingPlan: SortingPlan             |                                            |
 *    +------------------------------------------+                                            |
 *    | fun columnSortingPlan: ColumnSortingPlan |                                            |
 *    +------------------------------------------+                                            |
 *                          ^                                                                 |
 *                          |                          +----------------------------------+   |
 * User action as input:    |(owns)              +---->|      SortingPlanReducer (1)      |---+
 * (Column ID+Strategy)     |                    |     +----------------------------------+
 *     |                    |                    |     | Creates a new SortingPlan based  |
 *     |     +-----------------------------+     |     | upon the former plan, the newly  |
 *     |     |         StateStore          |     |     | column ID and its sorting        |
 *     |     +-----------------------------+     |     | strategy (asc, desc, none)       |
 *     +---->| val sortingChanged: Handler |-----+     +----------------------------------+
 *           +-----------------------------+
 *     +---->| fun renderingRowsData       |-----+
 *     |     +-----------------------------+     |     +----------------------------------+
 *     |                                         +---->|           RowSorter (2)          |
 *     |                                               +----------------------------------+
 *     |                                               | Takes the current rows and sort  |
 * TableComponent                                      | them according to the column     |
 * .renderRows()                                       | based sorting plan.              |
 *                                                     | So the raw ``sortingPlan``       |
 *                                                     | field of the state object must   |
 *                                                     | be  enriched before with the     |
 *                                                     | actual ``Column`` objects by     |
 *                                                     | the ``columnSoftingPlan``        |
 *                                                     | function.                        |
 *                                                     +----------------------------------+
 *
 *
 *                                                     +----------------------------------+
 * TableComponent ------------------------------------>|        SortingRenderer (3)       |
 * .renderHeader()                                     +----------------------------------+
 *                                                     | Renders the UI elements          |
 *                                                     | (icons eg.) for configuring      |
 *                                                     | and changing the sorting state.  |
 *                                                     +----------------------------------+
 *
 * [Edit](https://textik.com/#f3fc13858b89df9b)
 * ```
 * @see ColumnSortingPlan
 * @see SortingPlanReducer
 * @see RowSorter
 * @see SortingRenderer
 *
 */
typealias SortingPlan = List<ColumnIdSorting>

/**
 * This alias expresses the grouping of a [Column] paired with its sorting strategy.
 * Together with the [SortingPlan] this represents the *foundation* of the sorting mechanisms:
 * Based upon the [SortingPlan] this plan is created from the given [Column]s of a datatable and is then used to
 * do the actual sorting within a [RowSorter] implementation.
 *
 * There are three different interfaces on that the separate sorting functionalities are implemented:
 *  1. [SortingPlanReducer]: create a plan of columns to be sorted in a specific direction based upon the former
 *                           plan and the triggering user action
 *  2. [RowSorter]: do the actual sorting work based upon the above plan (slightly transformed to typed column
 *                  instead of plain ids)
 *  3. [SortingRenderer]: render the appropriate UI for the sorting actions in the header columns
 *
 * Here is the big picture visualized:
 * ```
 *                          +-----------------------------------------------------------------+
 *                          |                                   (emits new state)             |
 *                          |                                                                 |
 *                          v                                                                 |
 *    +------------------------------------------+                                            |
 *    |                  State                   |                                            |
 *    +------------------------------------------+                                            |
 *    | val sortingPlan: SortingPlan             |                                            |
 *    +------------------------------------------+                                            |
 *    | fun columnSortingPlan: ColumnSortingPlan |                                            |
 *    +------------------------------------------+                                            |
 *                          ^                                                                 |
 *                          |                          +----------------------------------+   |
 * User action as input:    |(owns)              +---->|      SortingPlanReducer (1)      |---+
 * (Column ID+Strategy)     |                    |     +----------------------------------+
 *     |                    |                    |     | Creates a new SortingPlan based  |
 *     |     +-----------------------------+     |     | upon the former plan, the newly  |
 *     |     |         StateStore          |     |     | column ID and its sorting        |
 *     |     +-----------------------------+     |     | strategy (asc, desc, none)       |
 *     +---->| val sortingChanged: Handler |-----+     +----------------------------------+
 *           +-----------------------------+
 *     +---->| fun renderingRowsData       |-----+
 *     |     +-----------------------------+     |     +----------------------------------+
 *     |                                         +---->|           RowSorter (2)          |
 *     |                                               +----------------------------------+
 *     |                                               | Takes the current rows and sort  |
 * TableComponent                                      | them according to the column     |
 * .renderRows()                                       | based sorting plan.              |
 *                                                     | So the raw ``sortingPlan``       |
 *                                                     | field of the state object must   |
 *                                                     | be  enriched before with the     |
 *                                                     | actual ``Column`` objects by     |
 *                                                     | the ``columnSoftingPlan``        |
 *                                                     | function.                        |
 *                                                     +----------------------------------+
 *
 *
 *                                                     +----------------------------------+
 * TableComponent ------------------------------------>|        SortingRenderer (3)       |
 * .renderHeader()                                     +----------------------------------+
 *                                                     | Renders the UI elements          |
 *                                                     | (icons eg.) for configuring      |
 *                                                     | and changing the sorting state.  |
 *                                                     +----------------------------------+
 *
 * [Edit](https://textik.com/#f3fc13858b89df9b)
 * ```
 * @see SortingPlan
 * @see SortingPlanReducer
 * @see RowSorter
 * @see SortingRenderer
 */
typealias ColumnSortingPlan<T> = List<Pair<Column<T>, Sorting>>


/**
 * This interface defines the actual sorting action.
 *
 * @see ColumnSortingPlan
 */
interface RowSorter<T> {
    fun sortedBy(
        rows: List<T>,
        columnSortingPlan: ColumnSortingPlan<T>
    ): List<T>
}

/**
 * This class implements the sorting with at most *one* column as sorting criterion, that is at most one column
 * within the [ColumnSortingPlan].
 *
 * If no column is present in the plan, no sorting will take place!
 */
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

/**
 * This interface bundles the methods to render the appropriate UI elements for the sorting actions within the
 * header column of a table.
 *
 * The data table takes care of calling the fitting method based upon the current state of a column's sorting
 * strategy.
 *
 * @see SortingPlan
 */
interface SortingRenderer {
    fun renderSortingActive(context: Div, sorting: Sorting)
    fun renderSortingLost(context: Div)
    fun renderSortingDisabled(context: Div)
}

/**
 * This implementation of a [SortingRenderer] creates an icon based UI for choosing the sorting of a data table.
 *  - There is an up-down-arrow icon, if the column is not sorted, but *sortable*
 *  - There is an down-arrow icon, if the data is sorted descending by the current column
 *  - There is an up-arrow icon, if the data is sorted ascending by the current column
 *  - There is no icon, if sorting is disabled for this column
 *
 *  @see SortingRenderer
 */
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

/**
 * This interface defines the reducing process for the sorting plan, that is the creation of a new plan based upon
 * a user input, like clicking on symbol or button ([SingleArrowSortingRenderer]).
 *
 * @see SortingPlan
 */
interface SortingPlanReducer {
    fun reduce(sortingPlan: SortingPlan, activated: ColumnIdSorting): SortingPlan
}

/**
 * This [SortingPlanReducer] implementation defines the logic to generate a plan with at most *one* column for sorting.
 * This perfectly matches with the [OneColumnSorter] implementation for doing the sorting.
 *
 * @see SortingPlan
 */
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
 * Central class for the *dynamic* aspects of the column configuration, so the actual *state* of column meta
 * data in contrast to the row data!
 *
 * Currently the order of the columns (which includes visibility!) and the [SortingPlan] are managed.
 *
 * There are two helper methods to ease the [State] handling:
 * - [orderedColumnsWithSorting]: Used for generating the needed structure for the header rendering
 * - [columnSortingPlan]: enriches the pure [SortingPlan] with the actual [Column]s, which is needed by the
 *                        [RowSorter] interface instead of the pure IDs only.
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

/**
 * Store for the column configuration that holds a [State] object.
 * It does **not** manage the actual data of the table; this is done by [RowSelectionStore]!
 *
 * Currently only one handler ([sortingChanged]) is offered, that calculates the new [SortingPlan] based upon the
 * selected [Column]-Id and its [Sorting] strategy. The required [SortingPlanReducer] is used to to the actual
 * calculation.
 *
 * On top there are some helper functions, that produces the needed [Flow]s of data for the different rendering
 * aspects: One for the header, one for the rows and one for the cells of a row.
 */
class StateStore<T, I>(private val sortingPlanReducer: SortingPlanReducer) : RootStore<State>(
    State(emptyList(), emptyList())
) {
    fun renderingHeaderData(component: TableComponent<T, I>) =
        data.map { it.orderedColumnsWithSorting(component.columns.value.columns) }

    fun renderingRowsData(component: TableComponent<T, I>) = component.dataStore.data.combine(data) { data, state ->
        component.options.value.sorting.value.sorter.value.sortedBy(
            data,
            state.columnSortingPlan(component.columns.value.columns)
        ).withIndex().toList()
    }

    fun renderingCellsData(component: TableComponent<T, I>, index: Int, row: T, selected: Flow<Boolean>) =
        renderingHeaderData(component).combine(selected) { columns, sel ->
            columns.map { (column, sorting) ->
                column to IndexedValue(
                    index,
                    StatefulItem(row, sel, sorting.strategy)
                )
            }
        }

    val sortingChanged = handle { state, activated: ColumnIdSorting ->
        state.copy(sortingPlan = sortingPlanReducer.reduce(state.sortingPlan, activated))
    }

    // TODO: Add handler for ordering / hiding or showing columns (change ``order`` property)
    //  Example for UI for changing: https://tailwindcomponents.com/component/table-ui-with-tailwindcss-and-alpinejs
}

/**
 * This store manages the selected rows of the data table.
 * It does **not** manage the state of the columns (configuration meta data like sorting, order and so on); this is
 * done by [StateStore]!
 */
class RowSelectionStore<T, I>(private val rowIdProvider: (T) -> I) : RootStore<List<T>>(emptyList()) {

    /**
     * The first item must always be dropped; need to find out why!
     */
    val selectedData = data.drop(1)

    fun isDataRowSelected(item: T) = data.map { selectedRows -> selectedRows.contains(item) }

    /**
     * This handler can be used to synchronize the selection with the actual data of the table.
     * If some item gets deleted, it should also disappear from the selection.
     */
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

    /**
     * This handler can be used to preselect one single row for the initial rendering of the table.
     * As the store is made to handle a [List] of rows as well as a single selection, we need this special
     * [Flow] for preselecting a single row. For preselecting arbitrary rows, just stick to the default [update]
     * handler!
     */
    val updateRow = handle<T?> { _, new ->
        if (new == null) emptyList() else listOf(new)
    }

    val dbClickedRow = handleAndEmit<T, T> { selectedRows, new ->
        emit(new)
        selectedRows
    }
}

/**
 * This interface defines a strategy type in order to implement different selection mechanisms.
 *
 * As we have identified already two different methods for selecting rows and provide default implementations for them,
 * it is necessary to separate the implementation from the core data table component.
 *
 * - [SelectionByClick]: selecting by clicking onto a row
 * - [SelectionByCheckBox]: selection by checkbox added with an additional column
 *
 * And of course we can use the strategy pattern, to provide the *null object* by [NoSelection] implementation, if
 * the selection is disabled for the table.
 */
interface SelectionStrategy<T, I> {
    fun manageSelectionByExtraColumn(component: TableComponent<T, I>)
    fun manageSelectionByRowEvents(
        component: TableComponent<T, I>, rowStore: SubStore<List<T>, List<T>, T>,
        renderContext: Tr
    )
}

/**
 * Selection strategy for disabling the selection at all.
 *
 * Acts as the *null object* pattern.
 *
 * @see [SelectionStrategy]
 */
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

/**
 * This selection strategy offers the selection via an additional checkbox per row, which is added in front of the
 * row as artificial column.
 *
 * @see [SelectionStrategy]
 */
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

/**
 * This selection strategy enables the selection by clicking onto a row of the table.
 *
 * @see [SelectionStrategy]
 */
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

/**
 * This class implements the core of the data table.
 *
 * It handles:
 * - the rendering of the table (see all ``render`` prefixed methods)
 * - the configuration (with sub-classes) and its DSL
 * - the state management like the current sorting or ordering of the columns with an internal store and lots of
 *   helper components like the whole infrastructure around the [SortingPlan]
 * - the selection of row(s) with an internal store and the usage of [SelectionStrategy] implementations
 * - the styling via theme and ad hoc definitions within the table column and header declarations
 */
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
    private val hoveringStyle = Theme().unsafeCast<DataTableTheme>().dataTableStyles.hoveringStyle
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

            val styling = ComponentProperty<BasicParams.(value: IndexedValue<StatefulItem<T>>) -> Unit> { }

            val content =
                ComponentProperty<Td.(
                    value: IndexedValue<StatefulItem<T>>,
                    cellStore: Store<String>?,
                    rowStore: SubStore<List<T>, List<T>, T>
                ) -> Unit>
                { _, store, _ -> store?.data?.asText() }
        }

        val columns: MutableMap<String, Column<T>> = mutableMapOf()

        fun column(
            styling: BasicParams.(value: IndexedValue<StatefulItem<T>>) -> Unit = {},
            expression: TableColumnContext<T>.() -> Unit
        ) {
            TableColumnContext<T>().apply(expression).also {
                it.styling(styling)
            }.build().also {
                columns[it.id] = it
            }
        }

        fun column(
            styling: BasicParams.(value: IndexedValue<StatefulItem<T>>) -> Unit = {},
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

        val styling = ComponentProperty<BoxParams.(IndexedValue<StatefulItem<T>>) -> Unit> { }
    }

    val columns = ComponentProperty(TableColumnsContext<T>())

    fun columns(
        styling: BoxParams.(IndexedValue<StatefulItem<T>>) -> Unit = {},
        expression: TableColumnsContext<T>.() -> Unit
    ) {
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

        class Hovering<T> {
            val active = ComponentProperty(true)
            val style = ComponentProperty<BasicParams.(IndexedValue<StatefulItem<T>>) -> Unit> {}
        }

        val hovering = ComponentProperty<Hovering<T>>(Hovering())
        fun hovering(value: Hovering<T>.() -> Unit) {
            hovering.value.apply(value)
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
                renderHeader({}, this)
                renderRows({
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
                renderHeader({
                    css("visibility:hidden")
                }, this)
                renderRows({}, rowIdProvider, this)
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
                renderHeader({}, this)
                renderRows({}, rowIdProvider, this)
            }
        }
    }

    private fun renderHeader(
        styling: GridParams.() -> Unit,
        renderContext: RenderContext
    ) {
        val component = this
        renderContext.apply {
            (::thead.styled() {
                styling()
            }) {
                tr {
                    component.stateStore.renderingHeaderData(component)
                        .renderEach(component.columnStateIdProvider) { (column, sorting) ->
                            (::th.styled(column.headerStyling) {
                                headerStyle(sorting.strategy)
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
                                            component.options.value.sorting.value.renderer.value.renderSortingLost(
                                                this
                                            )
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

    private fun <I> renderRows(
        styling: GridParams.() -> Unit,
        rowIdProvider: (T) -> I,
        renderContext: RenderContext
    ) {
        val component = this
        renderContext.apply {
            (::tbody.styled {
                styling()
            }) {
                component.stateStore.renderingRowsData(component)
                    .renderEach(IndexedValue<T>::hashCode) { (index, rowData) ->
                        val rowStore = component.dataStore.sub(rowData, rowIdProvider)
                        val isSelected = selectionStore.isDataRowSelected(rowStore.current)
                        tr {
                            if (options.value.hovering.value.active.value) {
                                className(isSelected.map { sel ->
                                    style {
                                        children("&:hover td") {
                                            hoveringStyle(
                                                IndexedValue(
                                                    index,
                                                    StatefulItem(
                                                        rowData as Any, // cast necessary, as theme can't depend on ``T``!
                                                        sel,
                                                        Sorting.NONE
                                                    )
                                                )
                                            )
                                            options.value.hovering.value.style.value(
                                                this, IndexedValue(
                                                    index,
                                                    StatefulItem(
                                                        rowData,
                                                        sel,
                                                        Sorting.NONE
                                                    )
                                                )
                                            )
                                        }
                                    }.name
                                })
                            }
                            selection.value.strategy.value?.manageSelectionByRowEvents(component, rowStore, this)
                            dblclicks.events.map { rowStore.current } handledBy component.selectionStore.dbClickedRow
                            component.stateStore.renderingCellsData(component, index, rowData, isSelected)
                                .renderEach { (column, statefulIndex) ->
                                    (::td.styled {
                                        columnStyle(
                                            this,
                                            // Can't just be copied, as Kotlin does not allow a cast within copy!
                                            IndexedValue(
                                                index,
                                                StatefulItem(
                                                    rowData as Any, // cast necessary, as theme can't depend on ``T``!
                                                    statefulIndex.value.selected,
                                                    statefulIndex.value.sorting
                                                )
                                            )
                                        )
                                        columns.value.styling.value(this, statefulIndex)
                                        column.styling(this, statefulIndex)
                                    }) {
                                        column.content(
                                            this,
                                            statefulIndex,
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
