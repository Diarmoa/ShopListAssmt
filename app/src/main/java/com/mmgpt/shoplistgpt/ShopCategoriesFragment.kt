package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.CategorySummaryAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding

class ShopCategoriesFragment : Fragment() {

    private val viewModel: ShoppingListViewModel by activityViewModels()
    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CategorySummaryAdapter()
        binding.recyclerView.adapter = adapter

        val gestureDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                // Wait for the release: changing page while the press is held
                // lets the release interrupt the ViewPager2 scroll.
                if (e.actionMasked != MotionEvent.ACTION_UP) return super.onDoubleTapEvent(e)
                // Convert raw screen coordinates to RecyclerView's coordinate system
                val location = IntArray(2)
                binding.recyclerView.getLocationOnScreen(location)
                val x = e.rawX - location[0]
                val y = e.rawY - location[1]
                val child = binding.recyclerView.findChildViewUnder(x, y)
                val position = child?.let { binding.recyclerView.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                if (position != RecyclerView.NO_POSITION) {
                    val viewPager = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
                    viewPager.currentItem = position
                    return true
                }
                return super.onDoubleTapEvent(e)
            }
        })

        binding.recyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            binding.tvHeading.text = getString(R.string.summary_heading)
            binding.tvCount.text = ""
            adapter.submitList(list)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
