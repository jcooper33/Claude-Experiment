package com.jcooper.tracker.ui.food

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
import com.jcooper.tracker.databinding.FragmentLogFoodBinding
import com.jcooper.tracker.util.DateTimeFormat
import kotlinx.coroutines.launch
import java.util.Calendar

class LogFoodFragment : Fragment() {

    private var _binding: FragmentLogFoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TrackerRepository
    private val earlierCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogFoodBinding.inflate(inflater, container, false)
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
        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()
        val caloriesText = binding.inputCalories.text?.toString()?.trim().orEmpty()
        val notes = binding.inputNotes.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }

        if (description.isEmpty()) {
            binding.inputDescription.error = getString(R.string.error_description_required)
            return
        }
        val calories = caloriesText.toIntOrNull()
        if (calories == null) {
            binding.inputCalories.error = getString(R.string.error_calories_required)
            return
        }

        val timestamp = if (binding.switchLogEarlier.isChecked) earlierCalendar.timeInMillis else System.currentTimeMillis()

        viewLifecycleOwner.lifecycleScope.launch {
            repository.logFood(description, calories, notes, timestamp)
            binding.textConfirmation.text = getString(
                R.string.food_confirmation,
                description,
                calories,
                DateTimeFormat.timestampLabel(timestamp),
            )
            binding.textConfirmation.visibility = View.VISIBLE
            binding.inputDescription.text?.clear()
            binding.inputCalories.text?.clear()
            binding.inputNotes.text?.clear()
            binding.switchLogEarlier.isChecked = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
