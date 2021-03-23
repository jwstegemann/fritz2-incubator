package dev.fritz2.components.datatable

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.Store
import dev.fritz2.binding.SubStore
import dev.fritz2.binding.sub
import dev.fritz2.components.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.Td
import dev.fritz2.dom.html.Tr
import dev.fritz2.dom.states
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.Lenses
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.name
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.Property
import dev.fritz2.styling.theme.Theme
import dev.fritz2.styling.whenever
import kotlinx.coroutines.flow.*

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
    val _id: String, // must be unique!
    val lens: Lens<T, String>? = null,
    val headerName: String = "",
    val minWidth: Property? = null,
    val maxWidth: Property? = null,
    val hidden: Boolean = false,
    val position: Int = 0,
    val sorting: Sorting = Sorting.NONE,
    val sortBy: Comparator<T>? = null,
    val styling: Style<BasicParams> = {},
    val content: Td.(cellStore: Store<String>?, rowStore: SubStore<List<T>, List<T>, T>?) -> Unit,
    val stylingHead: Style<BasicParams> = {},
    val contentHead: Div.(column: Column<T>) -> Unit
)

data class ColumnIdSorting(
    val id: String?,
    val strategy: Sorting = Sorting.NONE
) {
    companion object {
        fun noSorting() = ColumnIdSorting(null)

        fun <T> of(column: Column<T>) = ColumnIdSorting(column._id, column.sorting)
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

class SingleArrowSortingRenderer() : SortingRenderer {
    val sortDirectionSelected: Style<BasicParams> = {
        color { neutral }
    }

    val sortDirectionIcon: Style<BasicParams> = {
        width { "2rem" }
        height { "2rem" }
        color { primary.baseContrast }
        css("cursor:pointer;")
    }

    override fun renderSortingActive(context: Div, sorting: Sorting) {
        context.apply {
            when (sorting) {
                Sorting.NONE -> renderSortingLost((this))
                else -> icon({
                    sortDirectionIcon()
                    sortDirectionSelected()
                    size { normal }
                }) { fromTheme { if (sorting == Sorting.ASC) arrowUp else arrowDown } }
            }
        }
    }

    override fun renderSortingLost(context: Div) {
        context.apply {
            icon({
                sortDirectionIcon()
                size { normal }
            }) { fromTheme { sort } }
        }
    }

    override fun renderSortingDisabled(context: Div) {
        // just show nothing in this case
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

class StateStore(private val sortingPlanReducer: SortingPlanReducer) : RootStore<State>(
    State(emptyList(), emptyList())
) {

    val sortingChanged = handle { state, activated: ColumnIdSorting ->
        state.copy(sortingPlan = sortingPlanReducer.reduce(state.sortingPlan, activated))
    }

    // TODO: Add handler for ordering / hiding or showing columns (change ``order`` property)
    //  Example for UI for changing: https://tailwindcomponents.com/component/table-ui-with-tailwindcss-and-alpinejs
}


class RowSelectionStore<T> : MultiSelectionStore<T>() {
    val selectRows = toggle

    val selectRow = handleAndEmit<T?, T?> { _, new ->
        emit(new)
        if (new == null) {
            emptyList()
        } else {
            listOf(new)
        }
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
        content { _, rowStore ->
            checkbox(
                { margin { "0" } },
                id = uniqueId()
            ) {
                if (rowStore != null) {
                    checked(
                        component.selectionStore.data.map { selectedRows ->
                            selectedRows.contains(rowStore.current)
                        }
                    )
                    events {
                        clicks.events.map {
                            rowStore.current
                        } handledBy component.selectionStore.selectRows
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


/**
 * TODO open questions
 *  tfoot what will we do with this section of a table?
 *
 */
class TableComponent<T, I>(val dataStore: RootStore<List<T>>, protected val rowIdProvider: (T) -> I) :
    Component<Unit> {
    companion object {
        const val prefix = "table"
        val staticCss = staticStyle(
            prefix,
            """
                display:grid;
                min-width:100vw;
                flex: 1;
                display: grid;
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

        // TODO: Alles ins Theme packen!

        val sorterStyle: Style<BasicParams> = {
            display { flex }
            position {
                absolute {
                    right { "-1.125rem" }
                    top { "calc(50% -15px)" }
                }
            }
        }

        val defaultTbody: Style<BasicParams> = {

        }

        val defaultTr: Style<BasicParams> = {
            children("&:nth-child(odd) td") {
                background { color { gray100 } }
            }
        }

        val defaultTh: Style<BasicParams> = {
            background {
                color { primary.base }
            }
            verticalAlign { middle }
            color { primary.baseContrast }
            fontSize { normal }
            position { relative {} }
            paddings {
                vertical { smaller }
                left { smaller }
                right { large }
            }
            borders {
                right {
                    width { "1px" }
                    style { solid }
                    color { gray300 }
                }
            }

        }

        val defaultTd: Style<BasicParams> = {
            paddings {
                vertical { smaller }
                left { smaller }
                right { large }
            }
            background {
                color { gray300 }
            }
            borders {
                right {
                    width { "1px" }
                    style { solid }
                    color { gray700 }
                }
            }
        }
    }

    private val columnStateIdProvider: (Pair<Column<T>, ColumnIdSorting>) -> String = { it.first._id + it.second }

    class TableColumnsContext<T> {

        class TableColumnContext<T> {

            fun build(): Column<T> = Column(
                _id.value,
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

            val _id = ComponentProperty(uniqueId())
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
                val content = ComponentProperty<Div.(column: Column<T>) -> Unit> { config ->
                    +config.headerName
                }
            }

            private var header: HeaderContext<T> = HeaderContext()

            fun header(expression: HeaderContext<T>.() -> Unit) {
                header = HeaderContext<T>().apply(expression)
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
            val styling = ComponentProperty<Style<BasicParams>> {}

            val content =
                ComponentProperty<Td.(cellStore: Store<String>?, rowStore: SubStore<List<T>, List<T>, T>?) -> Unit>
                { store, _ -> store?.data?.asText() }
        }

        private var initialColumns: MutableMap<String, Column<T>> = mutableMapOf()

        val columns: Map<String, Column<T>>
            get() = initialColumns

        fun column(expression: TableColumnContext<T>.() -> Unit) {
            TableColumnContext<T>().apply(expression).build().also { initialColumns[it._id] = it }
        }

        fun column(title: String = "", expression: TableColumnContext<T>.() -> Unit) {
            TableColumnContext<T>().apply {
                header { title(title) }
                expression()
            }.build().also { initialColumns[it._id] = it }
        }

    }

    val columns = ComponentProperty<Map<String, Column<T>>>(mapOf())

    fun columns(expression: TableColumnsContext<T>.() -> Unit) {
        columns(TableColumnsContext<T>().apply(expression).columns)
    }

    fun prependAdditionalColumns(expression: TableColumnsContext<T>.() -> Unit) {
        val minPos = columns.value.values.minOf { it.position }
        columns(
            (columns.value.entries + TableColumnsContext<T>().apply(expression).columns
                .mapValues { it.value.copy(position = minPos - 1) }.entries)
                .map { (a, b) -> a to b }
                .toMap()
        )
    }

    val defaultTHeadStyle = ComponentProperty<Style<BasicParams>> {
        border {
            width { thin }
            style { solid }
            color { gray700 }
        }
    }
    val defaultThStyle = ComponentProperty<Style<BasicParams>> {}
    val defaultTBodyStyle = ComponentProperty<Style<BasicParams>> {}
    val defaultTdStyle = ComponentProperty<Style<BasicParams>> {}
    val defaultTrStyle = ComponentProperty<Style<BasicParams>> {}

    val selectedRowStyleClass = ComponentProperty(
        staticStyle(
            "selectedRow",
            "td { background-color: ${Theme().colors.secondary.base} !important; }"
        )
    )

    fun selectedRowStyle(value: Style<BasicParams>) {
        selectedRowStyleClass(staticStyle("customSelectedRow") { value() })
    }

    val selectionStore: RowSelectionStore<T> = RowSelectionStore()

    class EventsContext<T>(rowSelectionStore: RowSelectionStore<T>) {
        val selectedRows: Flow<List<T>> = rowSelectionStore.data
        val selectedRow: Flow<T?> = rowSelectionStore.selectRow
        val dbClicks: Flow<T> = rowSelectionStore.dbClickedRow
    }

    fun events(expr: EventsContext<T>.() -> Unit) {
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
            val row = ComponentProperty<Store<T?>?>(null)
            val selected = NullableDynamicComponentProperty<T>(emptyFlow())
        }

        internal var single: Single<T>? = null
        fun single(value: Single<T>.() -> Unit) {
            single = Single<T>().apply(value)
        }

        class Multi<T> {
            val rows = ComponentProperty<Store<List<T>>?>(null)
            val selected = DynamicComponentProperty<List<T>>(emptyFlow())
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

        internal val selectionMode by lazy {
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

        val fixedHeader = ComponentProperty(true)
        val fixedHeaderHeight = ComponentProperty<Property>("37px")
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

    private val stateStore: StateStore by lazy {
        StateStore(options.value.sorting.value.reducer.value)
    }

    fun <I> renderTable(
        styling: BoxParams.() -> Unit,
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
                    this.columns.value[itemId]?.let {
                        val min = it.minWidth ?: this.options.value.cellMinWidth.value
                        val max = it.maxWidth ?: this.options.value.cellMaxWidth.value
                        minmax += "\n" + if (min == max) {
                            max
                        } else {
                            if (!min.contains(Regex("px|%"))) {
                                "minmax($this.defaultMinWidth, $max)"
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


        if (component.options.value.fixedHeader.value) {
            renderFixedHeaderTable(
                styling,
                tableBaseClass,
                id,
                prefix,
                rowIdProvider,
                gridCols,
                RenderContext
            )
        } else {
            renderSimpleTable(
                styling,
                tableBaseClass,
                id,
                prefix,
                rowIdProvider,
                gridCols,
                RenderContext
            )
        }
    }

    private fun <I> renderFixedHeaderTable(
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String,
        rowIdProvider: (T) -> I,
        gridCols: Flow<String>,
        RenderContext: RenderContext
    ) {
        val component = this
        RenderContext.apply {
            (::table.styled({
                styling()
                height { component.options.value.fixedHeaderHeight.value }
                overflow { OverflowValues.hidden }
                position {
                    sticky {
                        top { "0" }
                    }
                }
            }, baseClass, "$id-fixedHeader", "$prefix-fixedHeader") {}){
                attr("style", gridCols)
                renderTHead({}, this)
                renderTBody({
                    css("visibility:hidden")
                }, rowIdProvider, this)
            }

            (::table.styled({
                styling()
                margins {
                    top { "-${component.options.value.fixedHeaderHeight.value}" }
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
        styling: BoxParams.() -> Unit,
        baseClass: StyleClass,
        id: String?,
        prefix: String,
        rowIdProvider: (T) -> I,
        gridCols: Flow<String>,
        RenderContext: RenderContext
    ) {

        RenderContext.apply {
            (::table.styled({
                styling()
            }, baseClass, id, prefix) {}){
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
                component.defaultTHeadStyle.value()
                styling()
            }) {
                tr {
                    component.stateStore.data.map { it.orderedColumnsWithSorting(component.columns.value) }
                        .renderEach(component.columnStateIdProvider) { (column, sorting) ->
                            (::th.styled(column.stylingHead) {
                                defaultTh()
                                component.defaultThStyle.value()
                            })  {
                                flexBox({
                                    height { "100%" }
                                    position { relative { } }
                                    alignItems { center }
                                }) {
                                    // Column Header Content
                                    with(column) { contentHead(this) }

                                    // Sorting
                                    (::div.styled(sorterStyle) {}){
                                        if (column._id == sorting.id) {
                                            component.options.value.sorting.value.renderer.value.renderSortingActive(
                                                this,
                                                sorting.strategy
                                            )
                                        } else if (column.sorting != Sorting.DISABLED) {
                                            component.options.value.sorting.value.renderer.value.renderSortingLost(this)
                                        } else {
                                            component.options.value.sorting.value.renderer.value.renderSortingDisabled(this)
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
                component.defaultTBodyStyle.value()
                styling()
            }) {
                component.dataStore.data.combine(component.stateStore.data) { data, state ->
                    component.options.value.sorting.value.sorter.value.sortedBy(
                        data,
                        state.columnSortingPlan(component.columns.value)
                    )
                }.renderEach(rowIdProvider) { t ->
                    val rowStore = component.dataStore.sub(t, rowIdProvider)
                    val selected = component.selectionStore.data.map { selectedRows ->
                        selectedRows.contains(rowStore.current)
                    }

                    (::tr.styled {
                        defaultTr()
                        component.defaultTrStyle.value()
                    }){
                        className(component.selectedRowStyleClass.value.whenever(selected).name)
                        selection.value.strategy.value?.manageSelectionByRowEvents(component, rowStore, this)
                        dblclicks.events.map { rowStore.current } handledBy component.selectionStore.dbClickedRow

                        component.stateStore.data.map { state -> state.order.mapNotNull { component.columns.value[it] } }
                            .renderEach { column ->
                                (::td.styled(column.styling) {
                                    defaultTd()
                                    component.defaultTdStyle.value()
                                }) {
                                    if (column.lens != null) {
                                        val b = rowStore.sub(column.lens)
                                        column.content(this, b, rowStore)
                                    } else {
                                        column.content(this, null, rowStore)
                                    }
                                }
                            }
                    }
                }
            }
        }
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
                columns.value.values.filter { !it.hidden }.sortedBy { it.position }.map { it._id },
                emptyList()
            )
        )

        context.apply {
            // preset selection via external store or flow
            when (selection.value.selectionMode) {
                SelectionMode.Single ->
                    (selection.value.single?.row?.value?.data
                        ?: selection.value.single?.selected!!.values) handledBy selectionStore.selectRow
                SelectionMode.Multi -> (selection.value.multi?.rows?.value?.data
                    ?: selection.value.multi?.selected!!.values) handledBy selectionStore.update
                else -> Unit
            }

            (::div.styled {
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
                renderTable(styling, baseClass, id, prefix, rowIdProvider, this)
            }

            // tie selection to external store if needed
            when (selection.value.selectionMode) {
                SelectionMode.Single -> events {
                    selection.value.single!!.row.value?.let { selectedRow handledBy it.update }
                }
                SelectionMode.Multi -> events {
                    selection.value.multi!!.rows.value?.let { selectedRows handledBy it.update }
                }
                else -> Unit
            }
        }
    }
}
