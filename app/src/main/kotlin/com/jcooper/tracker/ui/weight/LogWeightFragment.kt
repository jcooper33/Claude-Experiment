package com.jcooper.tracker.ui.weight

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jcooper.tracker.R
import com.jcooper.tracker.data.TrackerRepository
import com.jcooper.tracker.databinding.FragmentLogWeightBinding
import com.jcooper.tracker.util.DateTimeFormat
import kotlinx.coroutines.launch
import java.util.Calendar

class LogWeightFragment : Fragment() {

    private var _binding: FragmentLogWeightBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TrackerRepository
    private val earlierCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogWeightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = TrackerRepository(requireContext())

        binding.switchLogEarlier.setOnCheckedChangeListener { _, checked ->
            binding.earlierPickerRow.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) updatePickerButtonLabels()
        }

        binding.buttonPickDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    earlierCalendar.set(Calendar.YEAR, year)
                    earlierCalendar.set(Calendar.MONTH, month)
                    earlierCalendar.set(Calendar.DAY_OF_MONTH, day)
                    updatePickerButtonLabels()
                },
                earlierCalendar.get(Calendar.YEAR),
                earlierCalendar.get(Calendar.MONTH),
                earlierCalendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        binding.buttonPickTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    earlierCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    earlierCalendar.set(Calendar.MINUTE, minute)
                    updatePickerButtonLabels()
                },
                earlierCalendar.get(Calendar.HOUR_OF_DAY),
                earlierCalendar.get(Calendar.MINUTE),
                false,
            ).show()
        }

        binding.buttonSubmit.setOnClickListener { submit() }
    }

    private fun updatePickerButtonLabels() {
        binding.buttonPickDate.text = DateTimeFormat.dateLabel(earlierCalendar.timeInMillis)
        binding.buttonPickTime.text = DateTimeFormat.timeLabel(earlierCalendar.timeInMillis)
    }

    private fun submit() {
        val weightText = binding.inputWeight.text?.toString()?.trim().orEmpty()
        val notes = binding.inputNotes.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }

        val weight = weightText.toDoubleOrNull()
        if (weight == null) {
            binding.inputWeight.error = getString(R.string.error_weight_required)
            return
        }

        val timestamp = if (binding.switchLogEarlier.isChecked) earlierCalendar.timeInMillis else System.currentTimeMillis()

        viewLifecycleOwner.lifecycleScope.launch {
            repository.logWeight(weight, notes, timestamp)
            binding.textConfirmation.text = getString(
                R.string.weight_confirmation,
                weight.toString(),
                DateTimeFormat.timestampLabel(timestamp),
            )
            binding.textConfirmation.visibility = View.VISIBLE
            binding.inputWeight.text?.clear()
            binding.inputNotes.text?.clear()
            binding.switchLogEarlier.isChecked = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
