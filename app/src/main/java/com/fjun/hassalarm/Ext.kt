package com.fjun.hassalarm

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

fun setupInsets(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.statusBars(),
        )
        // Apply the insets as a margin to the view. This solution sets
        // only the bottom, left, and right dimensions, but you can apply whichever
        // insets are appropriate to your layout. You can also update the view padding
        // if that's more appropriate.
        v.updateLayoutParams<MarginLayoutParams> {
            leftMargin = insets.left
            bottomMargin = insets.bottom
            rightMargin = insets.right
            topMargin = insets.top
        }

        // Return CONSUMED if you don't want want the window insets to keep passing
        // down to descendant views.
        WindowInsetsCompat.CONSUMED
    }
}