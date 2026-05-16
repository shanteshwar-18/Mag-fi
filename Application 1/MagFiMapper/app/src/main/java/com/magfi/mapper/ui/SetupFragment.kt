package com.magfi.mapper.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.magfi.mapper.databinding.FragmentSetupBinding

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTextWatchers()
        setupStartButton()
    }

    private fun setupTextWatchers() {
        val clearErrorWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tilMapperName.error = null
                binding.tilBuildingName.error = null
                binding.tilFloorName.error = null
                binding.tilStartLandmark.error = null
            }
        }
        binding.etMapperName.addTextChangedListener(clearErrorWatcher)
        binding.etBuildingName.addTextChangedListener(clearErrorWatcher)
        binding.etFloorName.addTextChangedListener(clearErrorWatcher)
        binding.etStartLandmark.addTextChangedListener(clearErrorWatcher)
    }

    private fun setupStartButton() {
        binding.btnStartMapping.setOnClickListener {
            val mapperName = binding.etMapperName.text.toString().trim()
            val buildingName = binding.etBuildingName.text.toString().trim()
            val floorName = binding.etFloorName.text.toString().trim()
            val startLandmark = binding.etStartLandmark.text.toString().trim()

            if (mapperName.isEmpty() || buildingName.isEmpty() ||
                floorName.isEmpty() || startLandmark.isEmpty()
            ) {
                Snackbar.make(binding.root, getString(com.magfi.mapper.R.string.snackbar_fill_fields), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val action = SetupFragmentDirections.actionSetupFragmentToMappingFragment(
                mapperName = mapperName,
                buildingName = buildingName,
                floorName = floorName,
                startLandmark = startLandmark
            )
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
