package com.magfi.navigator.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.magfi.navigator.R
import com.magfi.navigator.databinding.FragmentHomeBinding
import com.magfi.navigator.databinding.ItemDestinationBinding
import com.magfi.navigator.viewmodel.DestinationItem
import com.magfi.navigator.viewmodel.NavigationViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: NavigationViewModel by activityViewModels()

    private var selectedDestinationId: String? = null
    private lateinit var destinationAdapter: DestinationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChipFilter()
        setupSearch()
        setupNavigateButton()
        observeStatus()

        // Show all destinations initially
        destinationAdapter.submitList(vm.destinations)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        destinationAdapter = DestinationAdapter { dest ->
            selectedDestinationId = dest.id
            binding.etDestinationSearch.setText(dest.title)
        }
        binding.rvDestinations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = destinationAdapter
        }
    }

    // ── Chip filter ───────────────────────────────────────────────────────────

    private fun setupChipFilter() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = when {
                checkedIds.contains(R.id.chipLab)       -> "Lab"
                checkedIds.contains(R.id.chipClassroom) -> "Classroom"
                checkedIds.contains(R.id.chipOffice)    -> "Office"
                checkedIds.contains(R.id.chipExit)      -> "Exit"
                else                                     -> null   // "All" or none
            }
            applyFilter(query = binding.etDestinationSearch.text?.toString() ?: "", category = category)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etDestinationSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val category = getActiveChipCategory()
                applyFilter(query, category)
            }
        })
    }

    private fun applyFilter(query: String, category: String?) {
        var filtered = vm.destinations
        if (!category.isNullOrBlank()) {
            // "Office" chip also shows "Staff" category
            filtered = if (category == "Office") {
                filtered.filter { it.category == "Office" || it.category == "Staff" }
            } else {
                filtered.filter { it.category == category }
            }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
            }
        }
        destinationAdapter.submitList(filtered)
    }

    private fun getActiveChipCategory(): String? {
        val checkedIds = binding.chipGroup.checkedChipIds
        return when {
            checkedIds.contains(R.id.chipLab)       -> "Lab"
            checkedIds.contains(R.id.chipClassroom) -> "Classroom"
            checkedIds.contains(R.id.chipOffice)    -> "Office"
            checkedIds.contains(R.id.chipExit)      -> "Exit"
            else                                     -> null
        }
    }

    // ── Navigate button ───────────────────────────────────────────────────────

    private fun setupNavigateButton() {
        binding.fabNavigate.setOnClickListener {
            val destId = selectedDestinationId
                ?: vm.destinations.firstOrNull {
                    it.title.equals(
                        binding.etDestinationSearch.text?.toString()?.trim(),
                        ignoreCase = true
                    )
                }?.id

            if (destId.isNullOrBlank()) {
                Snackbar.make(binding.root, getString(R.string.select_destination), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val action = HomeFragmentDirections.actionHomeFragmentToNavigationFragment(destId)
            findNavController().navigate(action)
        }
    }

    // ── Status badge ──────────────────────────────────────────────────────────

    private fun observeStatus() {
        vm.fingerprintCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatus.text = if (count > 0)
                "Block C · Floor 3 · $count fingerprints"
            else
                "Map loading…"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── ListAdapter ───────────────────────────────────────────────────────────

    // Not 'inner' — companion object is prohibited inside inner classes
    class DestinationAdapter(
        private val onItemClick: (DestinationItem) -> Unit
    ) : ListAdapter<DestinationItem, DestinationAdapter.ViewHolder>(DIFF_CALLBACK) {

        class ViewHolder(val binding: ItemDestinationBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDestinationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dest = getItem(position)
            holder.binding.tvDestinationName.text     = dest.title
            holder.binding.tvDestinationSubtitle.text = dest.subtitle
            holder.binding.tvDestinationCategory.text = dest.category

            // Category icon tint
            val (iconRes, colorHex) = when (dest.category) {
                "Lab"       -> Pair(R.drawable.ic_science, "#14FFEC")
                "Classroom" -> Pair(R.drawable.ic_school,  "#2979FF")
                "Staff"     -> Pair(R.drawable.ic_person,  "#FFB300")
                "Office"    -> Pair(R.drawable.ic_person,  "#FFB300")
                else        -> Pair(R.drawable.ic_stairs,  "#546E7A")
            }
            holder.binding.ivCategoryIcon.setImageResource(iconRes)
            holder.binding.ivCategoryIcon.setColorFilter(
                android.graphics.Color.parseColor(colorHex)
            )

            holder.itemView.setOnClickListener { onItemClick(dest) }
        }

        companion object {
            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DestinationItem>() {
                override fun areItemsTheSame(a: DestinationItem, b: DestinationItem) = a.id == b.id
                override fun areContentsTheSame(a: DestinationItem, b: DestinationItem) = a == b
            }
        }
    }
}
