package dev.fritz2.components

import dev.fritz2.binding.*
import dev.fritz2.components.datatable.*
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.params.*

fun <T, I> RenderContext.dataTable(
    styling: GridParams.() -> Unit = {},
    dataStore: RootStore<List<T>>,
    rowIdProvider: (T) -> I,
    baseClass: StyleClass = StyleClass.None,
    id: String? = null,
    prefix: String = TableComponent.prefix,
    build: TableComponent<T, I>.() -> Unit = {}
) {
    TableComponent(dataStore, rowIdProvider).apply(build).render(this, styling, baseClass, id, prefix)
}


