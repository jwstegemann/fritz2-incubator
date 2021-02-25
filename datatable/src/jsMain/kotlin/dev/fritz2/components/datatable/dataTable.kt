package dev.fritz2.components.datatable

import dev.fritz2.binding.RootStore
import dev.fritz2.binding.Store
import dev.fritz2.binding.SubStore
import dev.fritz2.components.MultiSelectionStore
import dev.fritz2.components.icon
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.Td
import dev.fritz2.lenses.Lens
import dev.fritz2.lenses.Lenses
import dev.fritz2.styling.params.BasicParams
import dev.fritz2.styling.params.Style
import dev.fritz2.styling.theme.Property

enum class Sorting {
    DISABLED,
    NONE,
    ASC,
    DESC
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
    val contentHead: Div.(column: Column<T>) -> Unit = { config ->
        +config.headerName
    }
) {
    fun applyContent(context: Div) {
        context.contentHead(this)
    }
}

typealias SortingPlan<T> = List<Pair<Column<T>, Sorting>>

data class ColumnIdSorting(
    val id: String?,
    val strategy: Sorting = Sorting.NONE
) {
    companion object {
        fun noSorting() = ColumnIdSorting(null)
    }
}

/*
Sortieren basiert auf drei unabhängigen Komponenten:
- Sortierkonfiguration + Änderung [fehlt noch!] SortingPlanReducer
- Rendern des Sortierbedienelements [SortingRenderer]
- Durchführung der Sortierung [TableSorter<T>]
 */


interface RowSorter<T> {
    fun sortedBy(
        rows: List<T>,
        sortingPlan: SortingPlan<T>
    ): List<T>
}

class SimpleRowSorter<T> : RowSorter<T> {
    override fun sortedBy(
        rows: List<T>,
        sortingPlan: SortingPlan<T>
    ): List<T> =
        if (sortingPlan.isNotEmpty()) {
            val (config, sorting) = sortingPlan.first()
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
        color { base }
    }

    val sortDirectionIcon: Style<BasicParams> = {
        width { "2rem" }
        height { "2rem" }
        color { lightGray }
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
    val sorting: List<ColumnIdSorting>,
) {
    fun <T> orderedColumnsWithSorting(columns: Map<String, Column<T>>):
            List<Pair<Column<T>, ColumnIdSorting>> =
        order.map { colId ->
            val sortingIndexForCurrentColumn = sorting.indexOfFirst { (id, _) -> id == colId }
            if (sortingIndexForCurrentColumn != -1) {
                columns[colId]!! to sorting[sortingIndexForCurrentColumn]
            } else {
                columns[colId]!! to ColumnIdSorting.noSorting()
            }
        }

    fun <T> sortingPlan(columns: Map<String, Column<T>>): List<Pair<Column<T>, Sorting>> =
        sorting.map { (colId, sorting) -> columns[colId]!! to sorting }
}


class StateStore : RootStore<State>(
    State(emptyList(), emptyList())
) {

    val sortingChanged = handle { state, id: String ->

        val new = if (state.sorting.isNotEmpty()) {
            if (state.sorting.first().id == id) {
                // TODO: Create interface to enable different behaviours (becomes important, if sorting over
                //  multiple columns will be available!
                when (state.sorting.first().strategy) {
                    Sorting.ASC -> Sorting.DESC
                    Sorting.DESC -> Sorting.NONE
                    else -> Sorting.ASC
                }
            } else {
                Sorting.ASC
            }
        } else {
            Sorting.ASC
        }

        state.copy(sorting = listOf(ColumnIdSorting(id, new)))
    }

    // TODO: Add handler for ordering / hiding or showing columns (change ``order`` property)
    //  Example for UI for changing: https://tailwindcomponents.com/component/table-ui-with-tailwindcss-and-alpinejs
}


class RowSelectionStore<T> : MultiSelectionStore<T>() {
    val selectRows = toggle

    val selectRow = handleAndEmit<T, T> { _, new ->
        emit(new)
        listOf(new)
    }

    val dbClickedRow = handleAndEmit<T, T> { selectedRows, new ->
        emit(new)
        selectedRows
    }
}