package com.mmgpt.shoplistgpt

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Opens a page when a row of [list] is double-clicked. [pageForRow] turns the
 * row's adapter position into a page number, or returns null to ignore the row.
 */
fun Fragment.openPageOnDoubleClick(list: RecyclerView, pageForRow: (Int) -> Int?) {
    val gestureDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            // Wait for the release: changing page while the press is held
            // lets the release interrupt the ViewPager2 scroll.
            if (e.actionMasked != MotionEvent.ACTION_UP) return false
            // Convert raw screen coordinates to RecyclerView's coordinate system
            val location = IntArray(2)
            list.getLocationOnScreen(location)
            val child = list.findChildViewUnder(e.rawX - location[0], e.rawY - location[1]) ?: return false
            val position = list.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) return false
            val page = pageForRow(position) ?: return false
            val viewPager = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
            // No animation: a slow page build can carry the scroll past its target.
            viewPager.setCurrentItem(page, false)
            return true
        }
    })

    list.setOnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        false
    }
}
