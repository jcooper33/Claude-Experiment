package com.jcooper.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.jcooper.tracker.R
import com.jcooper.tracker.data.SettingsStore
import com.jcooper.tracker.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settings: SettingsStore

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = SettingsStore(requireContext())

        binding.inputDailyTarget.setText(settings.dailyCalorieTarget.toString())
        binding.inputPlateauDays.setText(settings.plateauThresholdDays.toString())
        binding.inputPlateauTolerance.setText(settings.plateauToleranceLbs.toString())
        binding.inputDeficitDefault.setText(settings.deficitDefault.toString())

        binding.buttonSave.setOnClickListener {
            binding.inputDailyTarget.text?.toString()?.toIntOrNull()?.let { settings.dailyCalorieTarget = it }
            binding.inputPlateauDays.text?.toString()?.toIntOrNull()?.let { settings.plateauThresholdDays = it }
            binding.inputPlateauTolerance.text?.toString()?.toFloatOrNull()?.let { settings.plateauToleranceLbs = it }
            binding.inputDeficitDefault.text?.toString()?.toIntOrNull()?.let { settings.deficitDefault = it }

            binding.textSaved.text = getString(R.string.settings_saved)
            binding.textSaved.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
