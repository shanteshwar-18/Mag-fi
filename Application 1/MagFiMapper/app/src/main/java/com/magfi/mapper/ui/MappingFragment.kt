package com.magfi.mapper.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.magfi.mapper.R
import com.magfi.mapper.databinding.FragmentMappingBinding
import com.magfi.mapper.ui.adapter.WifiAdapter
import com.magfi.mapper.viewmodel.SensorViewModel
import org.json.JSONException
import org.json.JSONObject

class MappingFragment : Fragment() {

    private var _binding: FragmentMappingBinding? = null
    private val binding get() = _binding!!

    private val vm: SensorViewModel by activityViewModels()
    private lateinit var wifiAdapter: WifiAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMappingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve SafeArgs
        val args = MappingFragmentArgs.fromBundle(requireArguments())

        // Initialize session with passed metadata
        vm.initSession(args.mapperName, args.buildingName, args.floorName, args.startLandmark)

        setupRecyclerView()
        setupObservers()
        setupButtons()

        // Set building name in header
        binding.tvBuildingName.text = "${args.buildingName} — ${args.floorName}"

        // Start sensor engine
        vm.startEngine()
    }

    private fun setupRecyclerView() {
        wifiAdapter = WifiAdapter()
        binding.rvWifi.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = wifiAdapter
        }
    }

    private fun setupObservers() {
        vm.heading.observe(viewLifecycleOwner) { deg ->
            binding.tvHeading.text = "${deg.toInt()}°"
        }

        vm.stepCount.observe(viewLifecycleOwner) { count ->
            binding.tvSteps.text = count.toString()
        }

        vm.posX.observe(viewLifecycleOwner) { x ->
            binding.tvPosX.text = String.format("%+.2f", x)
        }

        vm.posY.observe(viewLifecycleOwner) { y ->
            binding.tvPosY.text = String.format("%+.2f", y)
        }

        vm.magData.observe(viewLifecycleOwner) { (bx, by, bz) ->
            binding.tvMag.text = "${bx.toInt()} | ${by.toInt()} | ${bz.toInt()}"
        }

        vm.rowCount.observe(viewLifecycleOwner) { count ->
            binding.tvRowCount.text = count.toString()
        }

        vm.wifiPayload.observe(viewLifecycleOwner) { payload ->
            val wifiList = parseWifiPayload(payload)
            wifiAdapter.submitList(wifiList)
        }

        vm.isRecording.observe(viewLifecycleOwner) { recording ->
            if (recording) {
                binding.chipStatus.text = getString(R.string.status_recording)
                binding.chipStatus.setTextColor(requireContext().getColor(R.color.colorAccent))
                binding.btnStart.isEnabled = false
                binding.btnStart.alpha = 0.4f
                binding.btnStop.isEnabled = true
                binding.btnStop.alpha = 1.0f
                binding.btnExport.isEnabled = false
                binding.btnExport.alpha = 0.4f
            } else {
                binding.chipStatus.text = getString(R.string.status_stopped)
                binding.chipStatus.setTextColor(requireContext().getColor(R.color.colorMuted))
                binding.btnStart.isEnabled = true
                binding.btnStart.alpha = 1.0f
                binding.btnStop.isEnabled = false
                binding.btnStop.alpha = 0.4f
                // BUG FIX #3: always enable export after STOP so the user can always save.
                // The row-count check was racing with stopRecording() and always saw 0.
                binding.btnExport.isEnabled = true
                binding.btnExport.alpha = 1.0f
            }
        }

        vm.calibrationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SensorViewModel.CalibrationState.Idle -> {
                    binding.calibrationOverlay.visibility = View.GONE
                }
                is SensorViewModel.CalibrationState.Counting -> {
                    binding.calibrationOverlay.visibility = View.VISIBLE
                    binding.tvCalibrationCount.text = state.secondsLeft.toString()
                    // Disable all buttons during calibration
                    binding.btnStart.isEnabled = false
                    binding.btnStop.isEnabled = false
                    binding.btnExport.isEnabled = false
                }
                is SensorViewModel.CalibrationState.Complete -> {
                    binding.calibrationOverlay.visibility = View.GONE
                    // Button states will be updated by isRecording observer
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            // Use viewLifecycleOwner.lifecycleScope — tied to fragment view lifecycle
            vm.startRecording(viewLifecycleOwner.lifecycleScope)
        }

        binding.btnStop.setOnClickListener {
            vm.stopRecording()
        }

        binding.btnExport.setOnClickListener {
            try {
                val file = vm.exportCsv(requireContext())
                Snackbar.make(binding.root, "Saved: ${file.name}", Snackbar.LENGTH_LONG)
                    .setAction("SHARE") {
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.provider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_csv)))
                    }
                    .show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun parseWifiPayload(payload: String): List<Pair<String, Int>> {
        return try {
            val json = JSONObject(payload)
            val result = mutableListOf<Pair<String, Int>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result.add(Pair(key, json.getInt(key)))
            }
            result.sortedByDescending { it.second }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Safety: stop engine when view is destroyed (back navigation)
        vm.stopEngine()
        _binding = null
    }
}
