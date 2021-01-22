import dev.fritz2.binding.RootStore
import dev.fritz2.binding.invoke
import dev.fritz2.binding.watch
import dev.fritz2.components.*
import dev.fritz2.dom.html.Div
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.values
import dev.fritz2.styling.params.AlignContentValues
import dev.fritz2.tracking.tracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map


@ExperimentalCoroutinesApi
fun RenderContext.progressDemo(): Div {

    return contentFrame {
        showcaseHeader("SimpleProgress")
        progressContent("progressDemo")
    }
}


@ExperimentalCoroutinesApi
fun RenderContext.progressContent(id: String): Div {

    val modal = modal(styling = {
        minHeight { "0" }
    }, id = "myModal") {

        content {
            lineUp {
                items {
                    icon({ color { "darkgreen" } }) { fromTheme { circleCheck } }
                    p { +"Done." }
                }
            }
        }
    }

    val progressDemoStore = object : RootStore<Int>(0) {
        val loading = tracker()
        val progress = handle<Int> { model, newProgressMade ->
            if (model + newProgressMade <= 100)
                model + newProgressMade
            else 100
        }
    }
    progressDemoStore.watch()

    val autoProgressStore = object : RootStore<Int>(0) {
        val loading = tracker()
        val showMsg = handle { current ->
            val startAt = (
                    if (current >= 100) 0
                    else current + 1
                    )
            flow {
                loading.track("Working..") {
                    for (i in startAt..100) {
                        delay(25)
                        emit(i)
                    }
                    modal()
                }
            } handledBy update
            100
        }
    }
    autoProgressStore.watch()

    return componentFrame {

        stackUp({
            width { full }
            verticalAlign { AlignContentValues.start }
            alignItems { start }
        }) {
            items {

                stackUp({
                    width { full }
                    verticalAlign { AlignContentValues.start }
                    alignItems { start }
                    margins { bottom { "2em" } }
                }) {
                    items {
                        stackUp({
                            width { full }
                            verticalAlign { AlignContentValues.start }
                        }) {
                            items {
                                spacing { larger }
                                simpleProgress ({
                                    radius { "0px" } // custom style: no rounded corners
                                }) {
                                    progress(progressDemoStore.data)
                                    color { secondary }
                                    backgroundColor { secondaryEffect }
                                    roundedTip(true ) // no effect (edges set straight by custom style radius = 0)
                                    size { small }
                                    label { flowOf("Small, using color and size options, and custom style for corners") }
                                }

                                simpleProgress {
                                    progress(progressDemoStore.data)
                                    label { flowOf("Normal size, no options, no custom styles") }
                                }

                                simpleProgress {
                                    roundedTip(true)
                                    progress(progressDemoStore.data)
                                    size { large }
                                    label { flowOf("Large, rounded progress bar tip") }
                                }

                                lineUp({
                                    width { full }
                                    justifyContent { flexStart }
                                    verticalAlign { top }
                                }) {
                                    items {

                                        clickButton {
                                            text("Progress 1%")
                                        }.map {
                                            1
                                        } handledBy progressDemoStore.progress

                                        clickButton {
                                            text("Progress 10%")
                                        }.map {
                                            10
                                        } handledBy progressDemoStore.progress

                                        clickButton {
                                            text("Reset")
                                        }.map { 0 } handledBy progressDemoStore.update

                                        inputField({
                                                width { "70px" }
                                            }) {
                                            base {
                                                type("number")
                                                min("0") // todo this still allows negative ints
                                                max("100")
                                                step("1")
                                                value(progressDemoStore.data.asString())
                                                changes.values().map {
                                                    it.toInt() // todo catch bad number
                                                } handledBy progressDemoStore.update
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                stackUp({
                    width { full }
                    verticalAlign { AlignContentValues.start }
                }) {
                    spacing { larger }
                    items {

                        simpleProgress({
                            css(
                                """
                                -webkit-transition: all 0s;
                                transition: all 0s;
                                """
                            )
                        }) {
                            roundedTip(true)
                            progress(autoProgressStore.data)
                            size { large }
                            label { flowOf("Rounded, using custom styles to remove css transitions") }
                        }

                        clickButton {
                            text("Start Progress")
                            loading(autoProgressStore.loading)
                        } handledBy autoProgressStore.showMsg
                    }

                }
            }
        }
    }
}