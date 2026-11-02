package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding
import com.mmgpt.shoplistgpt.ShoppingItem

class PlanCategoriesFragment : Fragment() {

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
        lateinit var adapter: ItemAdapter
        val dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                val itemFrom = adapter.currentList.getOrNull(from)
                val itemTo = adapter.currentList.getOrNull(to)
                if (itemFrom?.isPlaceholder == true || itemTo?.isPlaceholder == true) return false
                viewModel.reorderCategory(from - 1, to - 1)
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
        })

        adapter = ItemAdapter(
            onAction = { item, action ->
                val index = adapter.currentList.indexOf(item) - 1
                when (action) {
                    ItemAdapter.Action.EDIT -> showEditDialog(index, item.name)
                    ItemAdapter.Action.DELETE -> viewModel.deleteCategory(index)
                    else -> {}
                }
            },
            mode = ItemAdapter.Mode.PLAN,
            dragStart = { vh -> dragHelper.startDrag(vh) },
            onAdd = { position, name ->
                viewModel.addCategory(position - 1, name)
            }
        )
        binding.recyclerView.adapter = adapter
        dragHelper.attachToRecyclerView(binding.recyclerView)

        val gestureDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                // Wait for the release: changing page while the press is held
                // lets the release interrupt the ViewPager2 scroll.
                if (e.actionMasked != MotionEvent.ACTION_UP) return super.onDoubleTapEvent(e)
                val child = binding.recyclerView.findChildViewUnder(e.x, e.y)
                val position = child?.let { binding.recyclerView.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                if (position != RecyclerView.NO_POSITION) {
                    val item = adapter.currentList.getOrNull(position)
                    if (item?.isHeading == true) {
                        val index = position - 1
                        val viewPager = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
                        // No animation: a slow page build can carry the scroll past its target.
                        viewPager.setCurrentItem(index + 1, false)
                        return true
                    }
                }
                return super.onDoubleTapEvent(e)
            }
        })

        binding.recyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        binding.btnClearAll.visibility = View.VISIBLE
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_clear_all))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.clearAllCategories()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            binding.tvHeading.text = getString(R.string.categories_heading)
            binding.tvCount.text = ""
            val items = mutableListOf<ShoppingItem>()
            items.add(ShoppingItem(-1, "", isPlaceholder = true))
            list.forEach { cat ->
                items.add(ShoppingItem(cat.id, cat.heading, isHeading = true))
            }
            items.add(ShoppingItem(-2, "", isPlaceholder = true))
            adapter.submitList(items)
        }
    }

    private fun showEditDialog(index: Int, current: String) {
        val input = android.widget.EditText(requireContext()).apply { setText(current) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    viewModel.editCategory(index, text)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

