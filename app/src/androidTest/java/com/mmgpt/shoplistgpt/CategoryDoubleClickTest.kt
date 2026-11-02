package com.mmgpt.shoplistgpt

import android.content.Context
import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Double-clicks a category name the way a person does: the second press stays
 * down for a moment, so the page starts changing before the release arrives.
 * Each test tries several hold times because the bug only appears when the
 * release lands while the page is still moving.
 *
 * Run these with `adb shell am instrument`, not `connectedDebugAndroidTest`:
 * the Gradle task uninstalls the app afterwards, which erases its saved list.
 */
@RunWith(AndroidJUnit4::class)
class CategoryDoubleClickTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs = instrumentation.targetContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var savedState: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // The tests run against the real prefs file, so keep the user's list safe.
        savedState = prefs.getString(STATE_KEY, null)
        prefs.edit().putString(STATE_KEY, fixtureState()).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        prefs.edit().putString(STATE_KEY, savedState).commit()
    }

    @Test
    fun shopSummary_category1_opensItsPage() = assertShopSummaryOpens(category = 1)

    @Test
    fun shopSummary_category2_opensItsPage() = assertShopSummaryOpens(category = 2)

    @Test
    fun shopSummary_category10_opensItsPage() = assertShopSummaryOpens(category = 10)

    @Test
    fun planFirstPage_category3_opensItsPage() = assertPlanFirstPageOpens(category = 3)

    @Test
    fun planFirstPage_category6_opensItsPage() = assertPlanFirstPageOpens(category = 6)

    private fun assertShopSummaryOpens(category: Int) {
        clickStartButton(R.id.btnShop)
        assertDoubleClickOpens(category, startPage = HEADINGS.size)
    }

    private fun assertPlanFirstPageOpens(category: Int) {
        clickStartButton(R.id.btnPlan)
        assertDoubleClickOpens(category, startPage = 0)
    }

    private fun assertDoubleClickOpens(category: Int, startPage: Int) {
        val expected = HEADINGS[category - 1]
        val wrongPages = mutableListOf<String>()
        for (holdMs in SECOND_PRESS_HOLDS_MS) {
            showPage(startPage)
            doubleClick(findCategoryName(expected), holdMs)
            val opened = settledPageHeading()
            if (opened != expected) {
                wrongPages.add("held ${holdMs}ms: opened \"$opened\"")
            }
        }
        assertTrue(
            "Expected \"$expected\" but ${wrongPages.joinToString("; ")}",
            wrongPages.isEmpty()
        )
    }

    private fun clickStartButton(buttonId: Int) {
        onActivity { it.findViewById<View>(buttonId).performClick() }
    }

    private fun showPage(page: Int) {
        waitUntil("page $page to be shown") {
            val current = onActivity { activity ->
                val viewPager = activity.findViewById<ViewPager2>(R.id.viewPager)
                if (viewPager.isLaidOut) viewPager.setCurrentItem(page, false)
                viewPager.currentItem
            }
            current == page
        }
        settledPageHeading()
    }

    /** Finds the on-screen centre of the category's name in the visible list. */
    private fun findCategoryName(heading: String): Point {
        var point: Point? = null
        waitUntil("\"$heading\" to be listed") {
            point = onActivity { activity ->
                val list = visiblePage(activity).findViewById<RecyclerView>(R.id.recyclerView)
                val names = (0 until list.childCount)
                    .map { list.getChildAt(it).findViewById<TextView>(R.id.tvName) }
                val name = names.firstOrNull { it?.text?.toString() == heading }
                name?.let { screenCentre(activity, it) }
            }
            point != null
        }
        return point!!
    }

    private fun screenCentre(activity: MainActivity, view: View): Point {
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val centre = Point(viewLocation[0] + view.width / 2, viewLocation[1] + view.height / 2)

        val pager = activity.findViewById<ViewPager2>(R.id.viewPager)
        val pagerLocation = IntArray(2)
        pager.getLocationOnScreen(pagerLocation)
        if (centre.y >= pagerLocation[1] + pager.height) {
            fail("Category is below the bottom of the screen, so it cannot be clicked")
        }
        return centre
    }

    private fun doubleClick(point: Point, secondPressHoldMs: Long) {
        press(point, FIRST_PRESS_HOLD_MS)
        SystemClock.sleep(GAP_BETWEEN_PRESSES_MS)
        press(point, secondPressHoldMs)
    }

    private fun press(point: Point, holdMs: Long) {
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, point)
        SystemClock.sleep(holdMs)
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

    /**
     * Waits for the page on screen to stop moving, then reads its heading. This
     * watches the screen rather than ViewPager2's scroll state, which can stay
     * "settling" after a scroll is interrupted.
     */
    private fun settledPageHeading(): String {
        var last: Pair<String, Int>? = null
        var stableSince = SystemClock.uptimeMillis()
        val deadline = stableSince + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val current = onActivity { activity ->
                val page = visiblePage(activity)
                page.findViewById<TextView>(R.id.tvHeading).text.toString() to page.left
            }
            if (current != last) {
                last = current
                stableSince = SystemClock.uptimeMillis()
            } else if (current.first.isNotEmpty() &&
                SystemClock.uptimeMillis() - stableSince >= SETTLE_STABLE_MS
            ) {
                return current.first
            }
            SystemClock.sleep(POLL_MS)
        }
        fail("Page did not stop moving; last seen was $last")
        return ""
    }

    /** The page covering the middle of the pager, which is the one a person sees. */
    private fun visiblePage(activity: MainActivity): View {
        val viewPager = activity.findViewById<ViewPager2>(R.id.viewPager)
        val pages = viewPager.getChildAt(0) as ViewGroup
        return (pages as RecyclerView).findChildViewUnder(
            pages.width / 2f,
            pages.height / 2f
        ) ?: throw AssertionError("No page is on screen")
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_MS)
        }
        fail("Timed out waiting for $description")
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: Result<T>? = null
        scenario!!.onActivity { activity -> result = runCatching { block(activity) } }
        return result!!.getOrThrow()
    }

    private fun fixtureState(): String {
        val categories = JSONArray()
        HEADINGS.forEachIndexed { index, heading ->
            val item = JSONObject()
                .put("id", index.toLong())
                .put("name", "Item in $heading")
            categories.put(
                JSONObject()
                    .put("id", index.toLong())
                    .put("heading", heading)
                    .put("items", JSONArray().put(item))
            )
        }
        return JSONObject().put("categories", categories).toString()
    }

    private companion object {
        const val PREFS_NAME = "state"
        const val STATE_KEY = "state"

        const val FIRST_PRESS_HOLD_MS = 60L
        const val GAP_BETWEEN_PRESSES_MS = 100L
        val SECOND_PRESS_HOLDS_MS = listOf(60L, 100L, 150L, 200L)

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
    }
}
