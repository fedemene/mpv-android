package `is`.xyz.mpv.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import `is`.xyz.mpv.R
import `is`.xyz.mpv.Utils
import `is`.xyz.mpv.databinding.DialogSliderBinding
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slider preference that reads and writes `sub-scale=<value>` directly in `mpv.conf`.
 */
class SubScaleDialogPreference(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private val configFile = File("${context.filesDir.path}/mpv.conf")
    private lateinit var binding: DialogSliderBinding

    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()
        refreshSummary()
    }

    fun refreshSummary() {
        val current = readSubScale()
        summary = if (abs(current - DEFAULT_SCALE) < 0.001) {
            context.getString(R.string.pref_sub_scale_summary_default, formatDisplay(current))
        } else {
            context.getString(R.string.pref_sub_scale_summary, formatDisplay(current))
        }
    }

    override fun onClick() {
        super.onClick()
        binding = DialogSliderBinding.inflate(LayoutInflater.from(context))
        val padH = Utils.convertDp(context, 20f)
        val padTop = Utils.convertDp(context, 8f)
        binding.root.setPadding(padH, padTop, padH, 0)

        var currentValue = readSubScale()
        binding.seekBar.max = MAX_STEPS
        binding.seekBar.progress = valueToProgress(currentValue)
        updateValueLabel(currentValue)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentValue = progressToValue(progress)
                updateValueLabel(currentValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.resetBtn.setOnClickListener {
            currentValue = DEFAULT_SCALE
            binding.seekBar.progress = valueToProgress(DEFAULT_SCALE)
            updateValueLabel(DEFAULT_SCALE)
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                writeSubScale(currentValue)
                refreshSummary()
            }
            .create()
            .show()
    }

    private fun updateValueLabel(value: Double) {
        binding.textView.text = context.getString(R.string.pref_sub_scale_value, formatDisplay(value))
    }

    private fun readSubScale(): Double {
        if (!configFile.exists()) return DEFAULT_SCALE
        var result = DEFAULT_SCALE
        for (rawLine in configFile.readLines()) {
            val trimmed = rawLine.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val withoutComment = trimmed.substringBefore("#").trim()
            val eqIdx = withoutComment.indexOf('=')
            if (eqIdx <= 0) continue
            val key = withoutComment.substring(0, eqIdx).trim()
            if (key != "sub-scale") continue
            val valStr = withoutComment.substring(eqIdx + 1).trim().replace(',', '.')
            val parsed = valStr.toDoubleOrNull() ?: continue
            result = snapToStep(parsed.coerceIn(MIN_SCALE, MAX_SCALE))
        }
        return result
    }

    private fun writeSubScale(value: Double) {
        val snapped = snapToStep(value.coerceIn(MIN_SCALE, MAX_SCALE))
        val lines = if (configFile.exists()) configFile.readLines().toMutableList() else mutableListOf()

        // Remove existing active sub-scale lines (last one or duplicates) while keeping comments/other options
        lines.removeAll { rawLine ->
            val trimmed = rawLine.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@removeAll false
            val withoutComment = trimmed.substringBefore("#").trim()
            val eqIdx = withoutComment.indexOf('=')
            eqIdx > 0 && withoutComment.substring(0, eqIdx).trim() == "sub-scale"
        }

        if (abs(snapped - DEFAULT_SCALE) >= 0.001) {
            lines.add("sub-scale=${formatConfigValue(snapped)}")
        }

        val content = lines.joinToString("\n")
        if (content.isBlank()) {
            if (configFile.exists()) configFile.delete()
        } else {
            configFile.writeText("$content\n")
        }
    }

    companion object {
        private const val MIN_SCALE = 0.1
        private const val MAX_SCALE = 2.5
        private const val DEFAULT_SCALE = 1.0
        private const val STEP = 0.05
        private const val MAX_STEPS = 48 // (2.5 - 0.1) / 0.05

        private fun snapToStep(value: Double): Double {
            val steps = ((value - MIN_SCALE) / STEP).roundToInt().coerceIn(0, MAX_STEPS)
            return ((MIN_SCALE + steps * STEP) * 100.0).roundToInt() / 100.0
        }

        private fun valueToProgress(value: Double): Int =
            ((value - MIN_SCALE) / STEP).roundToInt().coerceIn(0, MAX_STEPS)

        private fun progressToValue(progress: Int): Double =
            ((MIN_SCALE + progress * STEP) * 100.0).roundToInt() / 100.0

        private fun formatConfigValue(value: Double): String {
            val formatted = String.format(Locale.US, "%.2f", value)
            return formatted.trimEnd('0').trimEnd('.')
        }

        private fun formatDisplay(value: Double): String =
            String.format(Locale.US, "%.2f", value)
    }
}
