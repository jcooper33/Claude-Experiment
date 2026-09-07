package com.jcooper.tracker.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jcooper.tracker.R
import com.jcooper.tracker.data.SettingsStore
import com.jcooper.tracker.data.TrackerRepository
import com.jcooper.tracker.databinding.FragmentDashboardBinding
import com.jcooper.tracker.export.ExportManager
import com.jcooper.tracker.logic.FoodLogRecord
import com.jcooper.tracker.logic.PlateauDetector
import com.jcooper.tracker.logic.RollingStatsCalculator
import com.jcooper.tracker.logic.WeightLogRecord
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: TrackerRepository
    private lateinit var settings: SettingsStore
    private lateinit var exportManager: ExportManager
    private val zone: ZoneId = ZoneId.systemDefault()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = TrackerRepository(requireContext())
        settings = SettingsStore(requireContext())
        exportManager = ExportManager(requireContext())

        binding.buttonExportXlsx.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val food = repository.allFoodRecords()
                val weight = repository.allWeightRecords()
                exportManager.exportXlsxAndShare(food, weight, settings)
            }
        }
        binding.buttonExportPdf.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val food = repository.allFoodRecords()
                val weight = repository.allWeightRecords()
                exportManager.exportPdfAndShare(food, weight, settings)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.observeAllFoodRecords(), repository.observeAllWeightRecords()) { food, weight ->
                    food to weight
                }.collect { (food, weight) -> render(food, weight) }
            }
        }
    }

    private fun render(food: List<FoodLogRecord>, weight: List<WeightLogRecord>) {
        val stats = RollingStatsCalculator(zone)
        val plateauDetector = PlateauDetector(zone, stats)
        val today = LocalDate.now(zone)
        val target = settings.dailyCalorieTarget

        val todayTotal = stats.caloriesForDay(food, today)
        binding.textTodayTotal.text = getString(R.string.today_total, todayTotal, target)

        binding.textAvgCalories7.text = getString(R.string.avg_calories_7, "%.0f".format(stats.averageDailyCalories(food, 7, today)))
        binding.textAvgCalories14.text = getString(R.string.avg_calories_14, "%.0f".format(stats.averageDailyCalories(food, 14, today)))

        val change7 = stats.averageWeightChangePerDay(weight, 7, today)
        val change14 = stats.averageWeightChangePerDay(weight, 14, today)
        binding.textAvgWeightChange7.text = getString(R.string.avg_weight_change_7, change7?.let { "%.2f".format(it) } ?: "n/a")
        binding.textAvgWeightChange14.text = getString(R.string.avg_weight_change_14, change14?.let { "%.2f".format(it) } ?: "n/a")

        binding.textDaysLogged.text = getString(R.string.days_logged, stats.daysLogged(food))
        binding.textDaysOverTarget.text = getString(R.string.days_over_target, stats.daysOverTarget(food, target))

        val plateauStatus = plateauDetector.detect(
            weight,
            today,
            windowDays = settings.plateauThresholdDays,
            toleranceLbs = settings.plateauToleranceLbs.toDouble(),
        )
        binding.textPlateauFlag.text = when {
            !plateauStatus.hasSufficientData -> getString(R.string.plateau_insufficient_data)
            plateauStatus.isPlateau -> getString(R.string.plateau_flag_on, "%.1f".format(plateauStatus.toleranceLbs), plateauStatus.windowDays)
            else -> getString(R.string.plateau_flag_off)
        }

        val dailyWeights = stats.dailyWeightAverages(weight).toSortedMap()
        binding.chartWeight.emptyText = getString(R.string.no_data_yet)
        binding.chartWeight.lineColor = ContextCompat.getColor(requireContext(), R.color.chart_weight)
        binding.chartWeight.points = dailyWeights.entries.map { (date, value) ->
            LineChartView.Point(x = date.toEpochDay().toFloat(), y = value.toFloat())
        }

        val dailyCalories = stats.dailyCalorieTotals(food).toSortedMap()
        binding.chartCalories.emptyText = getString(R.string.no_data_yet)
        binding.chartCalories.lineColor = ContextCompat.getColor(requireContext(), R.color.chart_calories)
        binding.chartCalories.points = dailyCalories.entries.map { (date, value) ->
            LineChartView.Point(x = date.toEpochDay().toFloat(), y = value.toFloat())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
