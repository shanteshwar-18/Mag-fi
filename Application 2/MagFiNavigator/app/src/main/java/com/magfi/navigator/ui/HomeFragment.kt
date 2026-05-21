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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.magfi.navigator.R
import com.magfi.navigator.databinding.FragmentHomeBinding
import com.magfi.navigator.databinding.ItemDestinationBinding
import com.magfi.navigator.viewmodel.NavigationViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: NavigationViewModel by activityViewModels()

    private var selectedDestination: String? = null

    // Full list of destinations (matches routing graph nodes)
    private val allDestinations = listOf(
        "Lab 301", "Lab 302", "Lab 303", "Library", "Cafeteria",
        "Stairs North", "Stairs South", "Main Entrance", "Main Exit",
        "Faculty Office", "Seminar Hall", "Corridor A", "Corridor B"
    )
    private val filteredDestinations = mutableListOf<String>().apply { addAll(allDestinations) }
    private lateinit var adapter: DestinationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupChips()
        setupNavigateButton()

        // Update status badge with fingerprint count
        vm.fingerprintCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatus.text = if (count > 0)
                "Map loaded — Floor 1 · $count fingerprints"
            else
                "Map loading…"
        }
    }

    private fun setupRecyclerView() {
        adapter = DestinationAdapter(filteredDestinations) { dest ->
            selectedDestination = dest
            binding.etDestinationSearch.setText(dest)
        }
        binding.rvDestinations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@HomeFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSearch() {
        binding.etDestinationSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                filterDestinations(query)
            }
        })
    }

    private fun setupChips() {
        val chips = listOf(
            binding.chipLab301   to "Lab 301",
            binding.chipLab302   to "Lab 302",
            binding.chipLibrary  to "Library",
            binding.chipCafeteria to "Cafeteria",
            binding.chipStairs   to "Stairs North",
            binding.chipMainExit to "Main Exit"
        )
        chips.forEach { (chip, label) ->
            chip.setOnClickListener {
                selectedDestination = label
                binding.etDestinationSearch.setText(label)
                filterDestinations(label)
            }
        }
    }

    private fun setupNavigateButton() {
        binding.fabNavigate.setOnClickListener {
            val dest = selectedDestination ?: binding.etDestinationSearch.text?.toString()?.trim()
            if (dest.isNullOrBlank()) {
                Snackbar.make(binding.root, getString(R.string.select_destination), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val action = HomeFragmentDirections
                .actionHomeFragmentToNavigationFragment(dest)
            findNavController().navigate(action)
        }
    }

    private fun filterDestinations(query: String) {
        filteredDestinations.clear()
        if (query.isBlank()) {
            filteredDestinations.addAll(allDestinations)
        } else {
            filteredDestinations.addAll(
                allDestinations.filter { it.contains(query, ignoreCase = true) }
            )
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── RecyclerView Adapter ─────────────────────────────────────────────────

    inner class DestinationAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<DestinationAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemDestinationBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDestinationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dest = items[position]
            holder.binding.tvDestinationName.text = dest
            holder.itemView.setOnClickListener { onItemClick(dest) }
        }

        override fun getItemCount() = items.size
    }
}
