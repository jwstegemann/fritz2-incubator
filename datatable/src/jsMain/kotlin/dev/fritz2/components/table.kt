package dev.fritz2.components

import dev.fritz2.binding.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.Td
import dev.fritz2.dom.html.Th
import dev.fritz2.dom.states
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.Lenses
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.BasicParams
import dev.fritz2.styling.params.GridParams
import dev.fritz2.styling.params.Style
import dev.fritz2.styling.params.styled
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.Property
import kotlinx.coroutines.flow.*


interface TableSorter<T> {
    fun sortedBy(
        elements: List<T>,
        config: TableComponent.TableColumn<T>,
        sorting: TableComponent.Companion.Sorting
    ): List<T>
}

class SimpleSorter<T> : TableSorter<T> {
    override fun sortedBy(
        elements: List<T>,
        config: TableComponent.TableColumn<T>,
        sorting: TableComponent.Companion.Sorting
    ): List<T> {
        return if (
            sorting != TableComponent.Companion.Sorting.DISABLED
            && sorting != TableComponent.Companion.Sorting.NONE
            && (config.lens != null || config.sortBy != null)
        ) {
            elements.sortedWith(
                createComparator(config, sorting),
            )
        } else elements
    }

    private fun createComparator(
        column: TableComponent.TableColumn<T>,
        sorting: TableComponent.Companion.Sorting
    ): Comparator<T> {
        if (column.sortBy == null) {
            return when (sorting) {
                TableComponent.Companion.Sorting.ASC -> {
                    compareBy { column.lens!!.get(it) }
                }
                else -> {
                    compareByDescending { column.lens!!.get(it) }
                }
            }
        } else {
            return when (sorting) {
                TableComponent.Companion.Sorting.ASC -> {
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
    fun renderSortingActive(context: Div, sorting: TableComponent.Companion.Sorting)
    fun renderSortingLost(context: Div)
    fun renderSortingDisabled(context: Div)
}

class UpDownSortingRenderer() : SortingRenderer {
    val sortDirectionSelected: Style<BasicParams> = {
        color { warning }
    }

    val sortDirectionIcon: Style<BasicParams> = {
        width { "0.9rem" }
        height { "0.9rem" }
        css("cursor:pointer;")
    }

    override fun renderSortingActive(context: Div, sorting: TableComponent.Companion.Sorting) {
        context.apply {
            icon({
                sortDirectionIcon()
                if (sorting == TableComponent.Companion.Sorting.ASC) {
                    sortDirectionSelected()
                }
                size { normal }
            }) { fromTheme { caretUp } }
            icon({
                sortDirectionIcon()
                if (sorting == TableComponent.Companion.Sorting.DESC) {
                    sortDirectionSelected()
                }
            }) { fromTheme { caretDown } }
        }
    }

    override fun renderSortingLost(context: Div) {
        context.apply {
            // we need some empty space to click!
            box({ sortDirectionIcon() }) { }
        }
    }

    override fun renderSortingDisabled(context: Div) {
        // nothing to render!
    }
}

class TogglingSymbolSortingRenderer() : SortingRenderer {
    val sortDirectionSelected: Style<BasicParams> = {
        color { warning }
    }

    // TODO: Icon needs bigger size!
    val sortDirectionIcon: Style<BasicParams> = {
        width { "2rem" }
        height { "2rem" }
        css("cursor:pointer;")
    }

    override fun renderSortingActive(context: Div, sorting: TableComponent.Companion.Sorting) {
        context.apply {
            when (sorting) {
                TableComponent.Companion.Sorting.NONE -> renderSortingLost((this))
                else -> icon({
                    sortDirectionIcon()
                    sortDirectionSelected()
                    size { normal }
                }) { fromTheme { if (sorting == TableComponent.Companion.Sorting.ASC) caretUp else caretDown } }
            }
        }
    }

    override fun renderSortingLost(context: Div) {
        context.apply {
            // we need some empty space to click!
            box({ sortDirectionIcon() }) { }
        }
    }

    override fun renderSortingDisabled(context: Div) {
        // nothing to render!
    }
}

class TableConfigStore : RootStore<TableComponent.TableState>(
    TableComponent.TableState(
        emptyList(),
        "" to TableComponent.Companion.Sorting.NONE,
    )
) {

    val sortingChanged = handle { state, id: String ->

        val new = if (state.sorting.first == id) {
            // TODO: Create interface to enable different behaviours (becomes important, if sorting over
            // multiple columns will be available!
            when (state.sorting.second) {
                TableComponent.Companion.Sorting.ASC -> TableComponent.Companion.Sorting.DESC
                TableComponent.Companion.Sorting.DESC -> TableComponent.Companion.Sorting.NONE
                else -> TableComponent.Companion.Sorting.ASC
            }
        } else {
            TableComponent.Companion.Sorting.ASC
        }

        state.copy(sorting = id to new)
    }

    // TODO: Add handler for ordering / hiding or showing columns (change ``order`` property)
    // Example for UI for changing: https://tailwindcomponents.com/component/table-ui-with-tailwindcss-and-alpinejs
}

class SelectionStore<T> : RootStore<List<T>>(emptyList()) {
    val selectRows = handleAndEmit<T, List<T>> { selectedRows, new ->
        val newSelection = if (selectedRows.contains(new))
            selectedRows - new
        else
            selectedRows + new
        emit(newSelection)
        newSelection
    }

    val selectRow = handleAndEmit<T, T> { selectedRows, new ->
        val newSelection = if (selectedRows.contains(new))
            selectedRows - new
        else
            selectedRows + new
        emit(new)
        newSelection
    }
}

/**
 * TODO open questions
 *  tfoot what will we do with this section of a table?
 *
 */
class TableComponent<T> {
    companion object {
        const val prefix = "table"
        val staticCss = staticStyle(
            prefix,
            """
                display:grid;
                min-width: 95vw;
                width: auto;
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
                  padding: 0.5rem 1.4rem .5rem 0.5rem;
                  &:last-child {
                    border-right: none;
                  }
                }
                
                td {
                    border-bottom: 1px solid inherit;
                }
                
                th {
                  position: sticky;
                  top: 0;
                  background: rgb(52, 58, 64);
                  text-align: left;
                  font-weight: normal;
                  font-size: 1.1rem;
                  color: rgb(226, 232, 240);
                  position: relative;
                }
                
                tr {
                    td { background: rgba(52, 58, 64, 0.1); }
                    &:nth-child(even) {
                        td {  background: rgba(52, 58, 64, 0.2); }
                    } 
                    &.selected {
                    td { background: rgba(255, 193, 7, 0.8) }
                    }
                }
            """
        )

        val sorterStyle: Style<BasicParams> = {
            display { inlineGrid }
            paddings { vertical { "0.35rem" } }
            height { "fitContent" }
            position {
                absolute {
                    right { "0.5rem" }
                    top { "0" }
                }
            }
        }

        enum class SelectionMode {
            NONE,
            SINGLE,
            SINGLE_CHECKBOX,
            MULTI
        }

        enum class Sorting {
            DISABLED,
            NONE,
            ASC,
            DESC
        }

        enum class CaptionPlacement {
            TOP,
            BOTTOM
        }

    }

    val configIdProvider: (Pair<TableColumn<T>, Pair<String, Sorting>>) -> String = { it.first._id + it.second }

    @Lenses
    data class TableColumn<T>(
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
        // TODO: Remove default
        val content: (
            renderContext: Td,
            cellStore: Store<String>?,
            rowStore: SubStore<List<T>, List<T>, T>?
        ) -> Unit = { renderContext, store, _ ->
            renderContext.apply {
                store?.data?.asText()
            }
        },
        val stylingHead: Style<BasicParams> = {},
        // TODO: Remove default
        val contentHead: Th.(tableColumn: TableColumn<T>) -> Unit = { config ->
            +config.headerName
        }
    ) {
        fun applyContent(context: Th) {
            context.contentHead(this)
        }
    }

    class TableColumnsContext<T> {

        class TableColumnContext<T> {

            // TODO: Enhance setup by setting a default comparator lens based
            // see createInitialComparator, if block and combineWithPreviousComparator if block!
            fun build(): TableColumn<T> = TableColumn(
                _id,
                lens,
                header.title,
                width?.min,
                width?.max,
                hidden,
                position,
                sorting,
                sortBy,
                styling,
                content,
                header.styling,
                header.content
            )

            private var _id: String = uniqueId()
            fun id(value: () -> String) {
                _id = value()
            }

            private var lens: Lens<T, String>? = null
            fun lens(value: () -> Lens<T, String>) {
                lens = value()
            }

            class WidthContext {
                var min: Property? = null
                fun min(value: () -> Property) {
                    min = value()
                }

                var max: Property? = null
                fun max(value: () -> Property) {
                    max = value()
                }

                fun minmax(value: () -> Property) {
                    max = value()
                    min = value()
                }
            }

            private var width: WidthContext? = WidthContext()

            fun width(expression: WidthContext.() -> Unit) {
                width = WidthContext().apply(expression)
            }

            class HeaderContext<T> {

                var title: String = ""
                fun title(value: () -> String) {
                    title = value()
                }

                var styling: Style<BasicParams> = {}
                fun styling(value: Style<BasicParams>) {
                    styling = value
                }

                var content: Th.(tableColumn: TableColumn<T>) -> Unit = { config ->
                    +config.headerName
                }

                fun content(expression: Th.(tableColumn: TableColumn<T>) -> Unit) {
                    content = expression
                }
            }

            private var header: HeaderContext<T> = HeaderContext<T>()

            fun header(expression: HeaderContext<T>.() -> Unit) {
                header = HeaderContext<T>().apply(expression)
            }

            private var hidden: Boolean = false
            fun hidden(value: () -> Boolean) {
                hidden = value()
            }

            private var position: Int = 0
            fun position(value: () -> Int) {
                position = value()
            }

            private var sorting: Sorting = Sorting.NONE
            fun sorting(value: () -> Sorting) {
                sorting = value()
                console.info("Setze Sorting auf:", sorting.toString())
            }

            private var sortBy: Comparator<T>? = null
            fun sortBy(value: () -> Comparator<T>) {
                sortBy = value()
            }

            private var styling: Style<BasicParams> = {}
            fun styling(value: Style<BasicParams>) {
                styling = value
            }

            private var content: (
                renderContext: Td,
                cellStore: Store<String>?,
                rowStore: SubStore<List<T>, List<T>, T>?
            ) -> Unit = { renderContext, store, _ ->
                renderContext.apply {
                    store?.data?.asText()
                }
            }

            fun content(
                expression: (
                    renderContext: Td,
                    cellStore: Store<String>?,
                    rowStore: SubStore<List<T>, List<T>, T>?
                ) -> Unit
            ) {
                content = expression
            }

        }

        private var initialColumns: MutableMap<String, TableColumn<T>> = mutableMapOf()

        val columns: Map<String, TableColumn<T>>
            get() = initialColumns

        fun column(expression: TableColumnContext<T>.() -> Unit) {
            TableColumnContext<T>().apply(expression).build().also { initialColumns[it._id] = it }
        }

        fun column(title: String = "", expression: TableColumnContext<T>.() -> Unit) {
            TableColumnContext<T>().apply {
                header { title { title } }
                expression()
            }.build().also { initialColumns[it._id] = it }
        }

    }

    var columns: Map<String, TableColumn<T>> = mapOf()

    fun columns(expression: TableColumnsContext<T>.() -> Unit) {
        columns = TableColumnsContext<T>().apply(expression).columns
    }

    fun prependAdditionalColumns(expression: TableColumnsContext<T>.() -> Unit) {
        val minPos = columns.values.minOf { it.position }
        columns =
            (columns.entries + TableColumnsContext<T>().apply(expression).columns
                .mapValues { it.value.copy(position = minPos - 1) }.entries)
                .map { (a, b) -> a to b }
                .toMap()
    }

    /**
     * Central type for dynamic column configuration state
     */
    data class TableState(
        val order: List<String>,
        val sorting: Pair<String, Sorting>,
    )

    val configStore = TableConfigStore()

    var sorter: SimpleSorter<T> = SimpleSorter()
    fun sorter(value: () -> SimpleSorter<T>) {
        sorter = value()
    }

    var sortingRenderer: SortingRenderer = UpDownSortingRenderer()
    fun sortingRenderer(value: () -> SortingRenderer) {
        sortingRenderer = value()
    }

    var defaultMinWidth: Property = "150px"
    var defaultMaxWidth: Property = "1fr"

    var defaultTHeadStyle: Style<BasicParams> = {}
    fun defaultTHeadStyle(value: (() -> Style<BasicParams>)) {
        defaultTHeadStyle = value()
    }

    var defaultThStyle: Style<BasicParams> = {}
    fun defaultThStyle(value: (() -> Style<BasicParams>)) {
        defaultThStyle = value()
    }

    var defaultTBodyStyle: Style<BasicParams> = {}
    fun defaultTBodyStyle(value: (() -> Style<BasicParams>)) {
        defaultTBodyStyle = value()
    }

    var defaultTdStyle: Style<BasicParams> = {}
    fun defaultTdStyle(value: (() -> Style<BasicParams>)) {
        defaultTdStyle = value()
    }

    // TODO defaultTrStyle

    var selectionMode: SelectionMode = SelectionMode.NONE
    fun selectionMode(value: SelectionMode) {
        selectionMode = value
    }

    val selectionStore: SelectionStore<T> = SelectionStore()
    class EventsContext<T>(private val selectionStore: SelectionStore<T>) {
        val selectedRows: Flow<List<T>> = selectionStore.selectRows
        val selectedRow: Flow<T> = selectionStore.selectRow
    }

    fun events(expr: EventsContext<T>.() -> Unit) {
        EventsContext(selectionStore).expr()
    }

    var tableStore: RootStore<List<T>> = storeOf(emptyList())
    fun tableStore(value: RootStore<List<T>>) {
        tableStore = value
    }

    var selectedRows: Flow<List<T>> = flowOf(emptyList())
    fun selectedRows(value: Flow<List<T>>) {
        selectedRows = value
    }

    var selectedRowEvent: SimpleHandler<T>? = null
    var selectedAllRowEvents: SimpleHandler<List<T>>? = null


    var captionPlacement: CaptionPlacement = CaptionPlacement.TOP
    fun captionPlacement(value: CaptionPlacement) {
        captionPlacement = value
    }

    var caption: (RenderContext.() -> Unit)? = null
    fun caption(value: (RenderContext.() -> Unit)) {
        caption = {
            (::caption.styled() {
                if (captionPlacement == CaptionPlacement.TOP) {
                    css("grid-area:header;")
                } else {
                    css("grid-area:footer;")
                }
            }){ value() }
        }
    }

    fun caption(value: String) {
        this.caption(flowOf(value))
    }

    fun caption(value: Flow<String>) {
        caption = {
            (::caption.styled() {
                if (captionPlacement == CaptionPlacement.TOP) {
                    css("grid-area:header;")
                } else {
                    css("grid-area:footer;")
                }
            }){ value.asText() }
        }
    }
}


fun <T, I> RenderContext.table(
    styling: GridParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = TableComponent.prefix,
    rowIdProvider: (T) -> I,
    build: TableComponent<T>.() -> Unit = {}
) {
    val component = TableComponent<T>().apply {
        build()
    }

    // TODO: Put into its own Interface; should wait until events context is finished
    component.prependAdditionalColumns {
        when (component.selectionMode) {
            TableComponent.Companion.SelectionMode.MULTI -> {
                column {
                    width {
                        min { "60px" }
                        max { "60px" }
                    }
                    header {
                        content {
                            checkbox({ display { inlineBlock } }, id = uniqueId()) {
                                checked {
                                    component.selectedRows.map {
                                        it.isNotEmpty() && it == component.tableStore.current
                                    }
                                }
                                events {
                                    // TODO: Remove ols events handling!
                                    component.selectedAllRowEvents?.let {
                                        changes.states().map { selected ->
                                            if (selected) {
                                                component.tableStore.current
                                            } else {
                                                emptyList()
                                            }
                                        } handledBy it
                                    }
                                }
                            }
                        }
                    }
                    content { ctx, _, rowStore ->
                        ctx.apply {
                            checkbox(
                                { margins { "0" } },
                                id = uniqueId()
                            ) {
                                if (rowStore != null) {
                                    // TODO: Remove ols events handling!
                                    checked {
                                        component.selectedRows.map { selectedRows ->
                                            selectedRows.contains(rowStore.current)
                                        }
                                    }
                                    events {
                                        component.selectedRowEvent?.let {
                                            clicks.events.map {
                                                rowStore.current
                                            } handledBy it
                                        }

                                    }
                                }

                            }
                        }
                    }
                    sorting { TableComponent.Companion.Sorting.DISABLED }
                }
            }
            TableComponent.Companion.SelectionMode.SINGLE_CHECKBOX -> {
                column {
                    width {
                        min { "60px" }
                        max { "60px" }
                    }
                    content { ctx, _, rowStore ->
                        ctx.apply {
                            checkbox(
                                { margins { "0" } },
                                id = uniqueId()
                            ) {
                                if (rowStore != null) {
                                    checked {
                                        // TODO: Remove ols events handling!
                                        component.selectedRows.map { selectedRows ->
                                            selectedRows.contains(rowStore.current)
                                        }
                                    }
                                    events {
                                        component.selectedRowEvent?.let {
                                            clicks.events.map {
                                                rowStore.current
                                            } handledBy it
                                        }

                                    }
                                }
                            }
                        }
                    }
                    sorting { TableComponent.Companion.Sorting.DISABLED }
                }
            }
            else -> {
            }
        }
    }

    component.configStore.update(
        TableComponent.TableState(
            component.columns.values.filter { !it.hidden }.sortedBy { it.position }.map { it._id },
            "" to TableComponent.Companion.Sorting.NONE
        )
    )

    val tableBaseClass = if (baseClass != null) {
        baseClass + TableComponent.staticCss
    } else {
        TableComponent.staticCss
    }

    val gridCols = component.configStore.data
        .map { (order, sorting) ->
            var minmax = ""
            var header = ""
            var footer = ""
            var main = ""


            order.forEach { itemId ->
                component.columns[itemId]?.let {
                    val min = it.minWidth ?: component.defaultMinWidth
                    val max = it.maxWidth ?: component.defaultMaxWidth

                    minmax += "minmax($min, $max)"
                    main += "main "
                    footer += "footer "
                    header += "header "
                }
            }

            """
            grid-template-columns: $minmax;                
            grid-template-rows: auto;
            grid-template-areas:
           "$header"
           "$main"
           "$footer";
           """
        }

    // TODO: Idea / Proposal: Extract potions of rendering (header, content, footer) into companion object
    // funtions in order to break function into smaller pieces?
    (::table.styled({
        styling()
    }, tableBaseClass, id, prefix) {}){
        attr("style", gridCols)
        if (component.captionPlacement == TableComponent.Companion.CaptionPlacement.TOP) {
            component.caption?.invoke(this)
        }
        (::thead.styled() {
            component.defaultTHeadStyle()
        }) {
            tr {
                component.configStore.data.map {
                    it.order.map { col ->
                        if (col == it.sorting.first) component.columns[col]!! to it.sorting else
                            component.columns[col]!! to ("" to TableComponent.Companion.Sorting.NONE)
                    }
                }
                    .renderEach(component.configIdProvider) { (colConfig, sorting) ->
                        (::th.styled(colConfig.stylingHead) {
                            component.defaultThStyle()

                        })  {
                            // Column Header Content
                            colConfig.applyContent(this)

                            // Sorting
                            (::div.styled(TableComponent.sorterStyle) {}){
                                if (component.sorter != null && colConfig._id == sorting.first) {
                                    component.sortingRenderer.renderSortingActive(this, sorting.second)
                                } else if (colConfig.sorting != TableComponent.Companion.Sorting.DISABLED) {
                                    component.sortingRenderer.renderSortingLost(this)
                                } else {
                                    component.sortingRenderer.renderSortingDisabled(this)
                                }
                                clicks.events.map { colConfig._id } handledBy component.configStore.sortingChanged
                            }
                        }
                    }
            }
        }
        tbody {
            component.tableStore.data.combine(component.configStore.data) { tableData, config ->
                if (component.sorter == null || config.sorting.first == "") {
                    tableData
                } else {
                    component.sorter!!.sortedBy(
                        tableData,
                        component.columns[config.sorting.first]!!,
                        config.sorting.second
                    )
                }
            }.renderEach(rowIdProvider) { t ->
                val rowStore = component.tableStore.sub(t, rowIdProvider)
                val currentRow = rowStore.current
                val selected = component.selectedRows.map { selectedRows ->
                    selectedRows.contains(currentRow)
                }

                tr {
                    className(selected.map {
                        if (it && component.selectionMode == TableComponent.Companion.SelectionMode.SINGLE) {
                            "selected"
                        } else {
                            ""
                        }
                    })
                    if (component.selectionMode == TableComponent.Companion.SelectionMode.SINGLE) {
                        clicks.events.map {
                            currentRow
                        } handledBy component.selectionStore.selectRow

                        // TODO: Remove ols events handling!
                        component.selectedRowEvent?.let {
                            clicks.events.map {
                                currentRow
                            } handledBy it
                        }
                    }

                    component.configStore.data.map { it.order.mapNotNull { component.columns[it] } }.renderEach { ctx ->
                        (::td.styled(ctx.styling) {}) {
                            if (ctx.lens != null) {
                                val b = rowStore.sub(ctx.lens)
                                ctx.content(this, b, rowStore)
                            } else {
                                ctx.content(this, null, rowStore)
                            }
                        }
                    }
                }
            }

            if (component.captionPlacement == TableComponent.Companion.CaptionPlacement.BOTTOM) {
                component.caption?.invoke(this)
            }

        }
    }

}
