package app.logic

import kotlin.properties.Delegates

class ScreenDimensions {

    var w_dp by Delegates.notNull<Float>()
    var w_px by Delegates.notNull<Float>()

    var h_dp by Delegates.notNull<Float>()
    var h_px by Delegates.notNull<Float>()
}