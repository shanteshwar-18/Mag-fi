package com.magfi.navigator.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.magfi.navigator.R
import com.magfi.navigator.viewmodel.NavigationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CalibrationFragment : Fragment() {

    private val viewModel: NavigationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calibration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make sure sensors are started so we can capture mag data
        viewModel.startSensors()

        lifecycleScope.launch {
            // Sleek loading animation delay (2.5 seconds)
            delay(2500)

            // Trigger background calibration with current mag data
            val bx = viewModel.magData.value?.first ?: 0f
            val by = viewModel.magData.value?.second ?: 0f
            viewModel.triggerCalibration(bx, by)

            // Navigate to Home screen
            findNavController().navigate(R.id.action_calibration_to_home)
        }
    }
}
