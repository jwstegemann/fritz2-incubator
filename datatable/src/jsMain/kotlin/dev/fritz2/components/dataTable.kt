package dev.fritz2.components

import dev.fritz2.binding.*
import dev.fritz2.components.datatable.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.Td
import dev.fritz2.dom.states
import dev.fritz2.identification.uniqueId
import dev.fritz2.lenses.Lens
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.name
import dev.fritz2.styling.params.*
import dev.fritz2.styling.staticStyle
import dev.fritz2.styling.theme.Property
import dev.fritz2.styling.theme.Theme
import dev.fritz2.styling.whenever
import kotlinx.coroutines.flow.*

/**
 * TODO open questions
 *  tfoot what will we do with this section of a table?
 *
 */
class TableComponent<T, I>(protected val dataStore: RootStore<List<T>>, protected val rowIdProvider: (T) -> I) :
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
                background { color { lightestGray } }
            }
        }

        val defaultTh: Style<BasicParams> = {
            background {
                color { dark }
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
                    color { darkerGray }
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
                color { base }
            }
            borders {
                right {
                    width { "1px" }
                    style { solid }
                    color { darkerGray }
                }
            }
        }

        /*
        enum class SelectionMode {
            NONE,
            SINGLE,
            SINGLE_CHECKBOX,
            MULTI
        }

         */

        // TODO: Ggf. ausbauen -> Wozu notwendig?
        enum class CaptionPlacement {
            TOP,
            BOTTOM
        }

    }

    private val columnStateIdProvider: (Pair<Column<T>, ColumnIdSorting>) -> String = { it.first._id + it.second }

    class TableColumnsContext<T> {

        class TableColumnContext<T> {

            // TODO: Enhance setup by setting a default comparator lens based
            // see createInitialComparator, if block and combineWithPreviousComparator if block!
            fun build(): Column<T> = Column(
                _id,
                lens,
                header.title,
                width?.min,
                width?.max,
                hidden,
                position,
                sorting.value(SortingContext),
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

                var content: Div.(column: Column<T>) -> Unit = { config ->
                    +config.headerName
                }

                fun content(expression: Div.(column: Column<T>) -> Unit) {
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

            object SortingContext {
                val disabled = Sorting.DISABLED
                val none = Sorting.NONE
                val asc = Sorting.ASC
                val desc = Sorting.DESC
            }

            val sorting = ComponentProperty<SortingContext.() -> Sorting> { none }

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

        private var initialColumns: MutableMap<String, Column<T>> = mutableMapOf()

        val columns: Map<String, Column<T>>
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

    var columns: Map<String, Column<T>> = mapOf()

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

    val selectionStore: RowSelectionStore<T> = RowSelectionStore()

    class EventsContext<T>(rowSelectionStore: RowSelectionStore<T>) {
        val selectedRows: Flow<List<T>> = rowSelectionStore.data
        val selectedRow: Flow<T?> = rowSelectionStore.selectRow
        val dbClicks: Flow<T> = rowSelectionStore.dbClickedRow
    }

    fun events(expr: EventsContext<T>.() -> Unit) {
        EventsContext(selectionStore).expr()
    }

    /*
    EventsContext(multiSelectionStore.toggle).apply {
        events.value(this)
        store?.let { selected handledBy it.update }
    }
     */

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

    /*
        selection {
            strategy { checkbox } // click
            customStrategy(SomeImplementation) // pass some custom strategy instead
            // handle by Store
            row(Store<T>)
            rows(Store<List<T>>)
            // preselect by Flow
            selectedRow(Flow<T>)
            selectedRows(Flow<List<T>>)
        }

        selection { single { row(someStore) } }
        selection { single { selected(someFlow) } }
        selection { multi { rows(someStore) } }
        selection { multi { selected(someFlow) } }
     */

    enum class SelectionMode {
        None,
        Single,
        Multi
    }

    class Selection<T> {

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

        val strategy = ComponentProperty<StrategyContext.() -> StrategyContext.StrategySpecifier> { click }
        //val customStrategy = ComponentProperty<SelectionStrategy?>(null)

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

    private var selection = Selection<T>()
    fun selection(value: Selection<T>.() -> Unit) {
        selection = Selection<T>().apply { value() }
    }

    class Options<T> {

        class Sorting<T> {
            val reducer = ComponentProperty<SortingPlanReducer>(TogglingSortingPlanReducer())
            val sorter = ComponentProperty<RowSorter<T>>(OneColumnSorter())
            val renderer = ComponentProperty<SortingRenderer>(SingleArrowSortingRenderer())
        }

        internal var sorting = Sorting<T>()
        fun sorting(value: Sorting<T>.() -> Unit) {
            sorting = Sorting<T>().apply { value() }
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


    private var options = Options<T>()
    fun options(value: Options<T>.() -> Unit) {
        options = Options<T>().apply { value() }
    }

    private val stateStore: StateStore by lazy {
        StateStore(options.sorting.reducer.value)
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
                    this.columns[itemId]?.let {
                        val min = it.minWidth ?: this.options.cellMinWidth.value
                        val max = it.maxWidth ?: this.options.cellMaxWidth.value
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
        styling: BoxParams.() -> Unit,
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
        styling: BoxParams.() -> Unit,
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
                    component.stateStore.data.map { it.orderedColumnsWithSorting(component.columns) }
                        .renderEach(component.columnStateIdProvider) { (column, sorting) ->
                            (::th.styled(column.stylingHead) {
                                defaultTh()
                                component.defaultThStyle()
                            })  {
                                flexBox({
                                    height { "100%" }
                                    position { relative { } }
                                    alignItems { center }
                                }) {
                                    // Column Header Content
                                    column.applyContent(this)

                                    // Sorting
                                    (::div.styled(sorterStyle) {}){
                                        if (column._id == sorting.id) {
                                            component.options.sorting.renderer.value.renderSortingActive(
                                                this,
                                                sorting.strategy
                                            )
                                        } else if (column.sorting != Sorting.DISABLED) {
                                            component.options.sorting.renderer.value.renderSortingLost(this)
                                        } else {
                                            component.options.sorting.renderer.value.renderSortingDisabled(this)
                                        }
                                        clicks.events.map { ColumnIdSorting.of(column) } handledBy component.stateStore.sortingChanged
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
                component.dataStore.data.combine(component.stateStore.data) { data, state ->
                    component.options.sorting.sorter.value.sortedBy(data, state.columnSortingPlan(component.columns))
                }.renderEach(rowIdProvider) { t ->
                    val rowStore = component.dataStore.sub(t, rowIdProvider)
                    val selected = component.selectionStore.data.map { selectedRows ->
                        selectedRows.contains(rowStore.current)
                    }

                    (::tr.styled {
                        defaultTr()
                        component.defaultTrStyle()
                    }){
                        className(component.selectedRowStyleClass.whenever(selected).name)

                        // TODO: Change to selection.strategy as comparison instead!
                        if (selection.selectionMode == SelectionMode.Single) {
                            clicks.events.map {
                                rowStore.current
                            } handledBy component.selectionStore.selectRow
                        }

                        dblclicks.events.map { rowStore.current } handledBy component.selectionStore.dbClickedRow

                        component.stateStore.data.map { state -> state.order.mapNotNull { component.columns[it] } }
                            .renderEach { column ->
                                (::td.styled(column.styling) {
                                    defaultTd()
                                    component.defaultTdStyle()
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
        baseClass: StyleClass?,
        id: String?,
        prefix: String
    ) {


        // TODO: Put into its own Interface; should wait until events context is finished
        prependAdditionalColumns {
            when (selection.selectionMode) {
                SelectionMode.Multi -> {
                    column {
                        width {
                            min { "60px" }
                            max { "60px" }
                        }
                        header {
                            content {
                                checkbox({ display { inlineBlock } }, id = uniqueId()) {
                                    checked(
                                        selectionStore.data.map {
                                            it.isNotEmpty() && it == dataStore.current
                                        }
                                    )
                                    events {

                                        changes.states().map { selected ->
                                            if (selected) {
                                                dataStore.current
                                            } else {
                                                emptyList()
                                            }
                                        } handledBy selectionStore.update

                                    }
                                }
                            }
                        }
                        content { ctx, _, rowStore ->
                            ctx.apply {
                                checkbox(
                                    { margin { "0" } },
                                    id = uniqueId()
                                ) {
                                    if (rowStore != null) {
                                        checked(
                                            selectionStore.data.map { selectedRows ->
                                                selectedRows.contains(rowStore.current)
                                            }
                                        )
                                        events {
                                            clicks.events.map {
                                                rowStore.current
                                            } handledBy selectionStore.selectRows
                                        }
                                    }

                                }
                            }
                        }
                        sorting { disabled }
                    }
                }
                SelectionMode.Single -> {
                    column {
                        width {
                            min { "60px" }
                            max { "60px" }
                        }
                        content { ctx, _, rowStore ->
                            ctx.apply {
                                checkbox(
                                    { margin { "0" } },
                                    id = uniqueId()
                                ) {
                                    if (rowStore != null) {
                                        checked(
                                            // TODO: Remove ols events handling!
                                            selectionStore.data.map { selectedRows ->
                                                selectedRows.contains(rowStore.current)
                                            }
                                        )
                                        events {

                                            clicks.events.map {
                                                rowStore.current
                                            } handledBy selectionStore.selectRow
                                        }
                                    }
                                }
                            }
                        }
                        sorting { Sorting.DISABLED }
                    }
                }
                else -> {
                }
            }
        }

        stateStore.update(
            State(
                columns.values.filter { !it.hidden }.sortedBy { it.position }.map { it._id },
                emptyList()
            )
        )

        context.apply {
            // preset selection via external store or flow
            when (selection.selectionMode) {
                SelectionMode.Single ->
                    (selection.single?.row?.value?.data
                        ?: selection.single?.selected!!.values) handledBy selectionStore.selectRow
                SelectionMode.Multi -> (selection.multi?.rows?.value?.data
                    ?: selection.multi?.selected!!.values) handledBy selectionStore.update
                else -> Unit
            }

            if (captionPlacement == Companion.CaptionPlacement.TOP) {
                caption?.invoke(this)
            }

            (::div.styled {
                options.width.value?.also { width { it } }
                options.height.value?.also { height { it } }
                options.maxHeight.value?.also { maxHeight { it } }
                options.maxWidth.value?.also { maxWidth { it } }

                if (options.height.value != null || options.width.value != null) {
                    overflow { OverflowValues.auto }
                }

                css("overscroll-behavior: contain")
                position { relative { } }
            }) {
                renderTable(styling, baseClass, id, prefix, rowIdProvider, this)
            }

            if (captionPlacement == Companion.CaptionPlacement.BOTTOM) {
                caption?.invoke(this)
            }

            // tie selection to external store if needed
            when (selection.selectionMode) {
                SelectionMode.Single -> events {
                    selection.single!!.row.value?.let { selectedRow handledBy it.update }
                }
                SelectionMode.Multi -> events {
                    selection.multi!!.rows.value?.let { selectedRows handledBy it.update }
                }
                else -> Unit
            }
        }
    }
}

fun <T, I> RenderContext.dataTable(
    styling: GridParams.() -> Unit = {},
    dataStore: RootStore<List<T>>,
    rowIdProvider: (T) -> I,
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = TableComponent.prefix,
    build: TableComponent<T, I>.() -> Unit = {}
) {
    TableComponent(dataStore, rowIdProvider).apply(build).render(this, styling, baseClass, id, prefix)
}


