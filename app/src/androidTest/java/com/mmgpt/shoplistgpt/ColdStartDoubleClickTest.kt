package com.mmgpt.shoplistgpt

import android.content.Context
import android.graphics.Point
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Double-clicks category 3 on the Plan first page straight after a fresh app
 * start, with categories the same size as Shop-Example.xlsx. Building those
 * large pages for the first time is slow, which is what lets the page change
 * run past its target.
 *
 * Run this class on its own so that it gets a new process. Once the app has
 * been running, pages build quickly and the bug no longer shows, so the test
 * refuses to run in a process that is already warmed up:
 *
 * adb shell am instrument -w -e class com.mmgpt.shoplistgpt.ColdStartDoubleClickTest
 *     com.mmgpt.shoplistgpt.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class ColdStartDoubleClickTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs = instrumentation.targetContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var savedState: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val processAgeMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        assertTrue(
            "Process is ${processAgeMs}ms old, so this is not a fresh start. " +
                "Run this class on its own.",
            processAgeMs < MAX_PROCESS_AGE_MS
        )

        // The test runs against the real prefs file, so keep the user's list safe.
        savedState = prefs.getString(STATE_KEY, null)
        prefs.edit().putString(STATE_KEY, fixtureState()).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        if (scenario != null) {
            prefs.edit().putString(STATE_KEY, savedState).commit()
        }
    }

    @Test
    fun planFirstPage_category3_opensItsPage_afterFreshStart() {
        val expected = HEADINGS[2]
        onActivity { it.findViewById<View>(R.id.btnPlan).performClick() }
        val name = findCategoryName(expected)
        // A person takes a moment to find the row, so nothing is mid-layout.
        SystemClock.sleep(LOOK_AT_PAGE_MS)

        doubleClick(name)

        assertEquals(expected, settledPageHeading())
    }

    private fun findCategoryName(heading: String): Point {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val point = onActivity { activity ->
                val page = visiblePage(activity) ?: return@onActivity null
                val list = page.findViewById<RecyclerView>(R.id.recyclerView)
                val names = (0 until list.childCount)
                    .map { list.getChildAt(it).findViewById<TextView>(R.id.tvName) }
                names.firstOrNull { it?.text?.toString() == heading }?.let { screenCentre(it) }
            }
            if (point != null) return point
            SystemClock.sleep(POLL_MS)
        }
        fail("Timed out waiting for \"$heading\" to be listed")
        throw AssertionError()
    }

    private fun screenCentre(view: View): Point {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Point(location[0] + view.width / 2, location[1] + view.height / 2)
    }

    private fun doubleClick(point: Point) {
        press(point)
        SystemClock.sleep(GAP_BETWEEN_PRESSES_MS)
        press(point)
    }

    private fun press(point: Point) {
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, point)
        SystemClock.sleep(PRESS_HOLD_MS)
        inject(downTime, MotionEvent.ACTION_UP, point)
    }

    private fun inject(downTime: Long, action: Int, point: Point) {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            point.x.toFloat(),
            point.y.toFloat(),
            0
        )
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue(
                "Touch event was not delivered",
                instrumentation.uiAutomation.injectInputEvent(event, true)
            )
        } finally {
            event.recycle()
        }
    }

    /** Waits for the page on screen to stop moving, then reads its heading. */
    private fun settledPageHeading(): String {
        var last: Pair<String, Int>? = null
        var stableSince = SystemClock.uptimeMillis()
        val deadline = stableSince + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val current = onActivity { activity ->
                val page = visiblePage(activity)
                page?.let { it.findViewById<TextView>(R.id.tvHeading).text.toString() to it.left }
            }
            if (current != last) {
                last = current
                stableSince = SystemClock.uptimeMillis()
            } else if (current != null && current.first.isNotEmpty() &&
                SystemClock.uptimeMillis() - stableSince >= SETTLE_STABLE_MS
            ) {
                return current.first
            }
            SystemClock.sleep(POLL_MS)
        }
        fail("Page did not stop moving; last seen was $last")
        throw AssertionError()
    }

    /** The page covering the middle of the pager, which is the one a person sees. */
    private fun visiblePage(activity: MainActivity): View? {
        val viewPager = activity.findViewById<ViewPager2>(R.id.viewPager)
        val pages = viewPager.getChildAt(0) as RecyclerView
        return pages.findChildViewUnder(pages.width / 2f, pages.height / 2f)
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: Result<T>? = null
        scenario!!.onActivity { activity -> result = runCatching { block(activity) } }
        return result!!.getOrThrow()
    }

    private fun fixtureState(): String {
        var nextItemId = 0L
        val categories = JSONArray()
        HEADINGS.forEachIndexed { index, heading ->
            val items = JSONArray()
            repeat(ITEM_COUNTS[index]) { number ->
                items.put(
                    JSONObject()
                        .put("id", nextItemId++)
                        .put("name", "$heading item ${number + 1}")
                )
            }
            categories.put(
                JSONObject()
                    .put("id", index.toLong())
                    .put("heading", heading)
                    .put("items", items)
            )
        }
        return JSONObject().put("categories", categories).toString()
    }

    private companion object {
        const val PREFS_NAME = "state"
        const val STATE_KEY = "state"

        const val MAX_PROCESS_AGE_MS = 30_000L

        const val PRESS_HOLD_MS = 60L
        const val GAP_BETWEEN_PRESSES_MS = 100L
        const val LOOK_AT_PAGE_MS = 2000L

        const val POLL_MS = 50L
        const val SETTLE_STABLE_MS = 600L
        const val SETTLE_TIMEOUT_MS = 5000L

        /** The categories in Shop-Example.xlsx, in the order the app loads them. */
        val HEADINGS = listOf(
            "1 Fruit + Veg",
            "2 Fruit & Veg",
            "3 Pasta, Rice, Tin Tomato",
            "4 Meat",
            "5 Dairy & Ham",
            "6 Bread",
            "7 Yogurt, Coleslaw",
            "8 Eggs, Oils, Ketchup",
            "9 Baking & Cereal",
            "10 Tins",
            "11 Crackers & Gluten Free",
            "12 Cleaning Products",
            "13 Cat Food",
            "14 Toiletries & Paper",
            "15 Soft Drinks",
            "16 Alcohol",
            "17 Crisps & Biscuits",
            "18 Frozen Food",
            "19 Tea, Coffee, Spreads",
            "20 Other Shop"
        )

        /** How many items each category has in Shop-Example.xlsx. */
        val ITEM_COUNTS = listOf(22, 14, 15, 15, 15, 5, 5, 10, 10, 10, 9, 17, 4, 11, 7, 2, 7, 8, 6, 4)
    }
}
