package top.niunaijun.blackboxa.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import top.niunaijun.blackboxa.R

/**
 * Flat Clipku navy background for the main screen.
 *
 * This used to be ZenithShaderView, an animated AGSL shader (scrolling grid,
 * radial cyan pulse, digital noise) over obsidian, with a radial-gradient
 * drawable fallback below API 33. The CloneKu design is flat navy #243370
 * with no gradients, so the shader and its animation loop are gone: this view
 * just paints the deep navy background colour. It is kept as a view (rather
 * than a plain background attribute) so the layout structure does not change.
 */
class CloneKuBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.color.cloneku_navy_deep)
    }
}
