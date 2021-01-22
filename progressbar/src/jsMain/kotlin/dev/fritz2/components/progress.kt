package dev.fritz2.components

import dev.fritz2.styling.StyleClass
import dev.fritz2.styling.StyleClass.Companion.plus
import dev.fritz2.styling.staticStyle
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.identification.uniqueId
import dev.fritz2.styling.params.*
import dev.fritz2.styling.theme.Colors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// todo remove border from staticCss and offer border functions (or find way to attach user border styles without it looking dumb)

@ExperimentalCoroutinesApi
interface ProgressSizes {
    val small: Style<BasicParams>
    val normal: Style<BasicParams>
    val large: Style<BasicParams>
}

@ExperimentalCoroutinesApi
class ProgressComponent {
    val sizes = object : ProgressSizes {
        override val small: Style<BasicParams> = {
            lineHeight { small } // todo: show percentage as text in bar?
            height { "0.6rem" }
            radius { "0.6rem" }
        }
        override val normal: Style<BasicParams> = {
            lineHeight { normal }
            height { "1.0rem" }
            radius { "1.0rem" }
        }
        override val large: Style<BasicParams> = {
            lineHeight { large }
            height { "1.4rem" }
            radius { "1.4rem" }
        }
    }

    companion object {
        val staticContainerCss = staticStyle(
            "simpleProgressContainer",
            """ 
            width: 100%;
            overflow: hidden;
            position: relative;
            """
        )
        val staticProgressBackgroundCss = staticStyle(
            "simpleProgressBackground",
            """ 
            width: 100%;
            overflow: hidden;
            position: relative;
            border: 1px solid gray;
            """
        )
        val staticProgressCss = staticStyle(
            "simpleProgressBar",
            """ 
            
            width: 100%;
            -webkit-transition: all 0.3s;
            transition: all 0.3s;
            """
            //border-right: 1px solid gray;
        )
    }

    var progress: Flow<Int> = flowOf(0) // @ div progressbar
    fun progress(value: Flow<Int>) {
        progress = value
    }

    var roundedTip: Style<BasicParams> = { radii { right { "0" } } } // @ div progressbar
    fun roundedTip(value: Boolean) {
        if (value)
            roundedTip = {}
    }

    @ExperimentalCoroutinesApi
    var color: Style<BasicParams> = { background { color { primaryEffect } } } // @ div progressbar
    fun color(value: Colors.() -> ColorProperty) {
        color = { background { color { value() } } }
    }

    var backgroundColor: Style<BasicParams> = { background { color { lightEffect } } } // @ div progressBackground
    fun backgroundColor(value: Colors.() -> ColorProperty) {
        backgroundColor = { background { color { value() } } }
    }

    var label: Flow<String> = flowOf("")
    fun label(value: Flow<String>) {
        label = value
    }

    var size: ProgressSizes.() -> Style<BasicParams> = { normal }
    fun size(value: ProgressSizes.() -> Style<BasicParams>) {
        size = value
    }
}

@ExperimentalCoroutinesApi
fun RenderContext.simpleProgress(
    styling: BasicParams.() -> Unit = {},
    baseClass: StyleClass? = null,
    id: String? = null,
    prefix: String = "simpleprogress",
    build: ProgressComponent.() -> Unit
) {
    val component = ProgressComponent().apply(build)

    val internalId = id ?: uniqueId()

    (::div.styled(
        baseClass = baseClass + ProgressComponent.staticContainerCss,
        id = "$internalId-container",
        prefix = prefix,
        styling = {} // do not pass user styling to container divs
    )) {

        label {
            `for`(internalId)
            component.label.asText()
        }

        (::div.styled(
            baseClass = baseClass + ProgressComponent.staticProgressBackgroundCss,
            id = "$internalId-background",
            prefix = prefix,
            styling = {
                component.backgroundColor()
                component.size.invoke(component.sizes)()
                styling()
            }
        )) {
            (::div.styled(
                baseClass = baseClass + ProgressComponent.staticProgressCss,
                id = internalId,
                prefix = prefix
            ) {
                component.color()
                component.size.invoke(component.sizes)()
                component.roundedTip()
                styling()
            }) {
                component.progress.render { progress ->
                    when (progress) {
                        in 1..100 ->
                            inlineStyle("width: $progress%;")
                        in 101..Int.MAX_VALUE ->
                            inlineStyle("width: 100%;")
                        else -> // zero or negative
                            inlineStyle("width: 0px;")
                    }
                }
            }
        }
    }
}
