package dev.fritz2.components

import dev.fritz2.binding.*
import dev.fritz2.components.TableComponent.Companion.defaultTd
import dev.fritz2.components.TableComponent.Companion.defaultTh
import dev.fritz2.components.TableComponent.Companion.defaultTr
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.Td
import dev.fritz2.dom.html.Th
import dev.fritz2.dom.states
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.Lenses
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.name
import dev.fritz2.styling.params.*
import dev.fritz2.styling.params.BorderStyleValues.none
import dev.fritz2.styling.params.DisplayValues.table
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.Property
import dev.fritz2.styling.theme.Theme
import dev.fritz2.styling.whenever
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

class DefaultSortingRenderer() : SortingRenderer {
    val sortDirectionSelected: Style<BasicParams> = {
        color { base }
    }

    val sortDirectionIcon: Style<BasicParams> = {
        width { "2rem" }
        height { "2rem" }
        color { gray }
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
                }) { fromTheme { if (sorting == TableComponent.Companion.Sorting.ASC) arrowUp else arrowDown } }
            }
        }
    }

    override fun renderSortingLost(context: Div) {
        context.apply {
            // we need some empty space to click!
            icon({
                sortDirectionIcon()
                size { normal }
            }) { fromTheme { sort } }
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

    val dbClickedRow = handleAndEmit<T, T> { selectedRows, new ->
        emit(new)
        selectedRows
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
            children("&:nth-child(even) td") {
                background { color { lighterGray } }
            }
        }

        val defaultTh: Style<BasicParams> = {
            background {
                color { primary }
            }
            verticalAlign { middle }
            color { base }
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
                    color { lightGray }
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
                color { lightGray }
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
        val contentHead: Div.(tableColumn: TableColumn<T>) -> Unit = { config ->
            +config.headerName
        }
    ) {
        fun applyContent(context: Div) {
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

                var content: Div.(tableColumn: TableColumn<T>) -> Unit = { config ->
                    +config.headerName
                }

                fun content(expression: Div.(tableColumn: TableColumn<T>) -> Unit) {
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

    var sortingRenderer: SortingRenderer = DefaultSortingRenderer()
    fun sortingRenderer(value: () -> SortingRenderer) {
        sortingRenderer = value()
    }

    var defaultTHeadStyle: Style<BasicParams> = {
        border {
            width { thin }
            style { solid }
            color { dark }
        }
    }

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

    var defaultTrStyle: Style<BasicParams> = {}
    fun defaultTrStyle(value: (() -> Style<BasicParams>)) {
        defaultTrStyle = value()
    }

    var selectedRowStyleClass: StyleClass = staticStyle(
        "selectedRow", """
        td { background-color: ${Theme().colors.primaryEffect} !important; }        
    """.trimIndent()
    )

    fun selectedRowStyle(value: Style<BasicParams>) {
        selectedRowStyleClass = staticStyle("customSelectedRow") {
            value()
        }
    }

    var selectionMode: SelectionMode = SelectionMode.NONE
    fun selectionMode(value: SelectionMode) {
        selectionMode = value
    }

    val selectionStore: SelectionStore<T> = SelectionStore()

    class EventsContext<T>(selectionStore: SelectionStore<T>) {
        val selectedRows: Flow<List<T>> = selectionStore.data
        val selectedRow: Flow<T> = selectionStore.selectRow
        val dbClicks: Flow<T> = selectionStore.dbClickedRow
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

    var captionPlacement: CaptionPlacement = CaptionPlacement.TOP
    fun captionPlacement(value: CaptionPlacement) {
        captionPlacement = value
    }

    var caption: (RenderContext.() -> Unit)? = null
    fun caption(value: (RenderContext.() -> Unit)) {
        caption = {
            box { value() }
        }
    }

    fun caption(value: String) {
        this.caption(flowOf(value))
    }

    fun caption(value: Flow<String>) {
        caption = {
            box { value.asText() }
        }
    }

    class TableOptions {
        val fixedHeader = ComponentProperty(true)
        val fixedHeaderHeight = ComponentProperty<Property>("37px")
        val width = ComponentProperty<Property?>("100%")
        val maxWidth = ComponentProperty<Property?>(null)
        val height = ComponentProperty<Property?>(null)
        val maxHeight = ComponentProperty<Property?>("97vh")
        val cellMinWidth = ComponentProperty<Property>("130px")
        val cellMaxWidth = ComponentProperty<Property>("1fr")
    }


    var options = TableOptions()
    fun options(value: TableOptions.() -> Unit) {
        options = TableOptions().apply { value() }
    }


    fun <I> renderTable(
        styling: GridParams.() -> Unit,
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

        val gridCols = component.configStore.data
            .map { (order, sorting) ->
                var minmax = ""
                var header = ""
                var footer = ""
                var main = ""

                order.forEach { itemId ->
                    this.columns[itemId]?.let {
                        val min = it.minWidth ?: this.options.cellMinWidth.value
                        val max = it.maxWidth ?: this.options.cellMaxWidth.value
                        minmax += "\n" + if (min == max) {
                            "$max"
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


        if (component.options.fixedHeader.value) {
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
        styling: GridParams.() -> Unit,
        baseClass: StyleClass?,
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
                height { component.options.fixedHeaderHeight.value }
                overflow { hidden }
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
                    top { "-${component.options.fixedHeaderHeight.value}" }
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
        styling: GridParams.() -> Unit,
        baseClass: StyleClass?,
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
        RenderContext: RenderContext
    ) {
        val component = this

        RenderContext.apply {
            (::thead.styled() {
                component.defaultTHeadStyle()
                styling()
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
                                defaultTh()
                                component.defaultThStyle()
                            })  {
                                flexBox({
                                    height { "100%" }
                                    position { relative { } }
                                    alignItems { center }
                                }) {
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
            }
        }
    }

    private fun <I> renderTBody(
        styling: GridParams.() -> Unit,
        rowIdProvider: (T) -> I,
        RenderContext: RenderContext
    ) {
        val component = this
        RenderContext.apply {
            (::tbody.styled {
                component.defaultTBodyStyle()
                styling()
            }) {
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

                    (::tr.styled {
                        defaultTr()
                        component.defaultTrStyle()
                    }){
                        className(component.selectedRowStyleClass.whenever(selected).name)

                        if (component.selectionMode == Companion.SelectionMode.SINGLE) {
                            clicks.events.map {
                                currentRow
                            } handledBy component.selectionStore.selectRow
                        }

                        dblclicks.events.map { currentRow } handledBy component.selectionStore.dbClickedRow

                        component.configStore.data.map { it.order.mapNotNull { component.columns[it] } }
                            .renderEach { ctx ->
                                (::td.styled(ctx.styling) {
                                    defaultTd()
                                    component.defaultTdStyle()
                                }) {
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
            }
        }


    }

}

fun <T, I> RenderContext.dataTable(
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
                                checked(
                                    component.selectedRows.map {
                                        it.isNotEmpty() && it == component.tableStore.current
                                    }
                                )
                                events {

                                    changes.states().map { selected ->
                                        if (selected) {
                                            component.tableStore.current
                                        } else {
                                            emptyList()
                                        }
                                    } handledBy component.selectionStore.update

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
                                    checked(
                                        // TODO: Remove ols events handling!
                                        component.selectionStore.data.map { selectedRows ->
                                            selectedRows.contains(rowStore.current)
                                        }
                                    )
                                    events {

                                        clicks.events.map {
                                            rowStore.current
                                        } handledBy component.selectionStore.selectRow
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


    if (component.captionPlacement == TableComponent.Companion.CaptionPlacement.TOP) {
        component.caption?.invoke(this)
    }

    (::div.styled {
        component.options.width.value?.also { width { it } }
        component.options.height.value?.also { height { it } }
        component.options.maxHeight.value?.also { maxHeight { it }  }
        component.options.maxWidth.value?.also {  maxWidth { it }  }

        if (component.options.height.value != null || component.options.width.value != null) {
            overflow { auto }
        }

        css("overscroll-behavior: contain")
        position { relative { } }
    }) {


        component.renderTable(styling, baseClass, id, prefix, rowIdProvider, this)


    }

    if (component.captionPlacement == TableComponent.Companion.CaptionPlacement.BOTTOM) {
        component.caption?.invoke(this)
    }

}


