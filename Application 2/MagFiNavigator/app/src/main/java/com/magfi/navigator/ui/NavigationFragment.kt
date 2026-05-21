package com.magfi.navigator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.magfi.navigator.R
import com.magfi.navigator.databinding.FragmentNavigationBinding
import com.magfi.navigator.viewmodel.LocalizationState
import com.magfi.navigator.viewmodel.NavigationViewModel
import com.magfi.navigator.core.GraphNode
import kotlin.math.pow
import kotlin.math.sqrt

class NavigationFragment : Fragment() {

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private val vm: NavigationViewModel by activityViewModels()
    private val args: NavigationFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMapCanvas()
        setupObservers()
        setupButtons()
        checkLocationPermission()

        // Start sensors and set destination from args
        vm.startSensors()
        if (args.destinationName.isNotBlank()) {
            vm.setDestination(args.destinationName)
            binding.tvDestination.text = args.destinationName
        }
    }

    private fun setupMapCanvas() {
        val map = binding.mapCanvasView
        // Pass pre-loaded floor plan data from ViewModel
        map.floorBitmap      = vm.floorPlanManager.bitmap
        map.scalePxPerMeter  = vm.floorPlanManager.scalePxPerMeter
        map.originPixelX     = vm.floorPlanManager.originPixelX
        map.originPixelY     = vm.floorPlanManager.originPixelY
    }

    private fun setupObservers() {
        // Position → Blue Dot
        vm.posX.observe(viewLifecycleOwner) { x ->
            val y = vm.posY.value ?: 0f
            binding.mapCanvasView.updatePosition(x, y)
            binding.tvPosX.text = "${"%.2f".format(x)}m"
        }
        vm.posY.observe(viewLifecycleOwner) { y ->
            val x = vm.posX.value ?: 0f
            binding.mapCanvasView.updatePosition(x, y)
            binding.tvPosY.text = "${"%.2f".format(y)}m"
        }

        // Route → map polyline + ETA
        vm.currentRoute.observe(viewLifecycleOwner) { path ->
            binding.mapCanvasView.updateRoute(path ?: emptyList())
            binding.tvEta.text = computeEtaText(path)
        }

        // HUD metrics
        vm.heading.observe(viewLifecycleOwner) { deg ->
            binding.tvHeading.text = "${deg.toInt()}°"
        }
        vm.stepCount.observe(viewLifecycleOwner) { count ->
            binding.tvSteps.text = count.toString()
        }
        vm.fingerprintCount.observe(viewLifecycleOwner) { count ->
            binding.tvFp.text = "$count loaded"
        }

        // Accuracy state
        vm.localizationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LocalizationState.Fused -> {
                    binding.tvAccuracy.text      = getString(R.string.accuracy_fused)
                    binding.tvAccuracy.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.colorSecondary)
                    )
                    binding.chipLowAccuracy.visibility = View.GONE
                }
                is LocalizationState.PdrOnly -> {
                    binding.tvAccuracy.text      = getString(R.string.accuracy_pdr_only)
                    binding.tvAccuracy.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.colorNode)
                    )
                    binding.chipLowAccuracy.visibility = View.VISIBLE
                }
                is LocalizationState.WaitingForStep -> {
                    binding.tvAccuracy.text      = getString(R.string.accuracy_fused)
                    binding.tvAccuracy.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.colorSecondary)
                    )
                    binding.chipLowAccuracy.visibility = View.GONE
                }
            }
        }

        // Sensor / DB errors
        vm.sensorError.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK") { vm.sensorError.value = null }
                    .show()
            }
        }

        // Route errors
        vm.routeError.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                vm.routeError.value = null
            }
        }
    }

    private fun setupButtons() {
        // Re-route button
        binding.btnReroute.setOnClickListener {
            vm.reRoute()
        }

        // Clear button — reset route
        binding.btnClear.setOnClickListener {
            vm.currentRoute.value = null
            binding.mapCanvasView.updateRoute(emptyList())
            binding.tvEta.text = "Route cleared"
        }

        // Re-center FAB
        binding.fabRecenter.setOnClickListener {
            binding.mapCanvasView.recenter(zoom = 2f)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.location_permission_title))
                .setMessage(getString(R.string.location_permission_body))
                .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1001
                    )
                }
                .setNegativeButton(getString(R.string.continue_anyway)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    /** Compute human-readable ETA from route path. */
    private fun computeEtaText(path: List<GraphNode>?): String {
        if (path == null || path.size < 2) return "Route unavailable"
        val hops = path.size - 1
        val distM = path.zipWithNext { a, b ->
            sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))
        }.sum()
        return "$hops nodes · ${distM.toInt()}m"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vm.stopSensors()
        _binding = null
    }
}
