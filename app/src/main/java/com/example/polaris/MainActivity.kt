package com.example.polaris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.polaris.presentation.service.PolarisService
import com.example.polaris.util.PermissionUtils
import com.example.polaris.data.local.db.PolariaDatabase
import android.widget.Button
import android.widget.TextView
import android.widget.ScrollView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import android.view.ViewGroup
import android.graphics.Color
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.app.Activity
import android.content.res.ColorStateList
import android.view.animation.AccelerateDecelerateInterpolator
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.widget.ProgressBar
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.view.children


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.cancelAndJoin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat.performHapticFeedback
import com.google.android.material.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class CellularDataExport(
    val id: Long,
    val deviceId: String,
    val timestamp: Long,
    val timestampFormatted: String, // Human-readable timestamp
    val latitude: Double,
    val longitude: Double,
    val technology: String,
    val plmnId: String,
    val lac: Int,
    val rac: Int,
    val tac: Int,
    val cellId: Long,
    val frequencyBand: String,
    val arfcn: Int,
    val actualFrequency: String,
    val rsrp: Int,
    val rsrq: Int,
    val rscp: Int,
    val ecNo: Int,
    val rxLev: Int,
    val sinr: Int,
    val operatorName: String,
    val notes: String,
    val httpUploadRate: Double,
    val pingResponseTime: Double,
    val dnsResponseTime: Double,
    val webResponseTime: Double,
    val smsDeliveryTime: Double,
    val testNotes: String
)

class MainActivity : AppCompatActivity() {
    private val STORAGE_PERMISSION_REQUEST_CODE = 102
    private val signupJob = SupervisorJob()
    private val signupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val BASE_URL = "http://46.249.99.35:8000"
    private var password: String = ""
    private val client = OkHttpClient()
    private lateinit var audioManager: AudioManager


    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val SMS_PERMISSION_REQUEST = 101
        private const val TAG = "MainActivity"
        const val ACTION_DRIVE_TEST_DATA = "com.example.polaris.DRIVE_TEST_DATA"
        const val ACTION_DRIVE_TEST_STATUS = "com.example.polaris.DRIVE_TEST_STATUS"
        private const val PREFS_NAME = "polaris_identity"
        private const val KEY_UNIQUE_NUMBER = "unique_number"
    }

    private lateinit var etSmsNumber: EditText
    private lateinit var etPingIp: EditText
    private lateinit var etDnsDomain: EditText
    private lateinit var etHttpUploadUrl: EditText
    private lateinit var etWebTestUrl: EditText
    private lateinit var statusIndicator: LinearLayout
    private var selectedTests = mutableSetOf<String>()
    private lateinit var testSelectionContainer: LinearLayout
    private lateinit var etIntervalSeconds: EditText
    private lateinit var exportButton: Button

    private lateinit var database: PolariaDatabase
    private lateinit var startDriveTestBtn: Button
    private lateinit var finishDriveTestBtn: Button
    private lateinit var statusText: TextView
    private lateinit var dataDisplay: TextView
    private lateinit var dataScrollView: ScrollView
    private lateinit var statusIcon: TextView
    private lateinit var statusCard: CardView
    private lateinit var progressBar: ProgressBar
    private lateinit var dataCountText: TextView
    private lateinit var connectionStatusText: TextView

    private var isDriveTestActive = false
    private var dataCount = 0

    private fun updateStartButtonState() {
        if (!::startDriveTestBtn.isInitialized) return



        startDriveTestBtn.text = "🚗 Start Drive Test (${selectedTests.size} tests)"
    }


    private fun selectAllTests() {
        testSelectionContainer.children.filterIsInstance<CheckBox>().forEach { checkbox ->
            checkbox.isChecked = true
        }
    }

    private fun clearAllTests() {
        testSelectionContainer.children.filterIsInstance<CheckBox>().forEach { checkbox ->
            checkbox.isChecked = false
        }
    }

    private fun createTestCheckbox(testName: String, emoji: String): CheckBox {
        return CheckBox(this).apply {
            text = "$emoji $testName"
            textSize = 16f
            setTextColor(Color.parseColor("#2D3748"))
            setPadding(16, 12, 16, 12)

            // Custom checkbox styling
            background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(1, Color.parseColor("#E2E8F0"))
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTests.add(testName)
                    background = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(Color.parseColor("#EDF2F7"))
                        setStroke(2, Color.parseColor("#4299E1"))
                    }
                } else {
                    selectedTests.remove(testName)
                    background = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(Color.parseColor("#FFFFFF"))
                        setStroke(1, Color.parseColor("#E2E8F0"))
                    }
                }
                // Only update button state if button is initialized
                updateStartButtonState()
            }

            // Initially select all tests
            isChecked = true
            selectedTests.add(testName)
        }
    }


    private fun createTestSelectionUI(): LinearLayout {
        // Outer container for all tests
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setColor(Color.parseColor("#F7FAFC"))
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
        }

        // Title
        container.addView(TextView(this).apply {
            text = "📊 Select Network Tests"
            textSize = 18f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // List of tests (name, emoji, hint for target‐field)
        val tests = listOf(
            Triple("SMS Test", "📱", "SMS Phone Number"),
            Triple("Ping Test", "🏓", "IP or Hostname (e.g. 8.8.8.8)"),
            Triple("DNS Test", "🌐", "DNS Domain (e.g. example.com)"),
            Triple("HTTP Upload", "📤", "Upload URL (e.g. https://...)"),
            Triple("Web Test", "🔗", "Web Test URL (e.g. https://...)")
        )

        tests.forEach { (testName, emoji, targetHint) ->
            // 1) Container for this single test (checkbox + its two EditTexts)
            val testSection = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 16)
            }

            // 2) The checkbox line
            val checkbox = CheckBox(this).apply {
                text = "$emoji $testName"
                textSize = 16f
                setTextColor(Color.parseColor("#2D3748"))
                setPadding(16, 12, 16, 12)
                background = GradientDrawable().apply {
                    cornerRadius = 8f
                    setColor(Color.parseColor("#FFFFFF"))
                    setStroke(1, Color.parseColor("#E2E8F0"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }

                // Default: checked
                isChecked = true
                selectedTests.add(testName)
            }
            testSection.addView(checkbox)

            // 3) “Target” EditText (assign to class field, enabled only if checkbox is checked)
            val etTargetLocal = EditText(this).apply {
                hint = targetHint
                inputType = when (testName) {
                    "SMS Test"      -> InputType.TYPE_CLASS_PHONE
                    "Ping Test"     -> InputType.TYPE_CLASS_TEXT
                    "DNS Test"      -> InputType.TYPE_CLASS_TEXT
                    "HTTP Upload",
                    "Web Test"      -> InputType.TYPE_TEXT_VARIATION_URI
                    else            -> InputType.TYPE_CLASS_TEXT
                }
                setTextColor(Color.parseColor("#2D3748"))
                setHintTextColor(Color.parseColor("#A0AEC0"))
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(Color.parseColor("#F7FAFC"))
                    setStroke(1, Color.parseColor("#E2E8F0"))
                }
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(32, 8, 32, 8) }
                isEnabled = checkbox.isChecked
            }
            // Assign to the correct class‐level property:
            when (testName) {
                "SMS Test"      -> etSmsNumber       = etTargetLocal
                "Ping Test"     -> etPingIp          = etTargetLocal
                "DNS Test"      -> etDnsDomain       = etTargetLocal
                "HTTP Upload"   -> etHttpUploadUrl   = etTargetLocal
                "Web Test"      -> etWebTestUrl      = etTargetLocal
            }
            testSection.addView(etTargetLocal)


            // 5) Checkbox toggle listener: enable/disable its two fields
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTests.add(testName)
                    checkbox.background = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(Color.parseColor("#EDF2F7"))
                        setStroke(2, Color.parseColor("#4299E1"))
                    }
                } else {
                    selectedTests.remove(testName)
                    checkbox.background = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(Color.parseColor("#FFFFFF"))
                        setStroke(1, Color.parseColor("#E2E8F0"))
                    }
                }
                etTargetLocal.isEnabled = isChecked
                updateStartButtonState()
            }

            container.addView(testSection)
        }

        // 6) “Select All” / “Clear All” buttons at bottom
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)

            addView(Button(this@MainActivity).apply {
                text = "✅ Select All"
                textSize = 14f
                setTextColor(Color.parseColor("#38A169"))
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#F0FFF4"))
                    setStroke(2, Color.parseColor("#38A169"))
                }
                setPadding(20, 10, 20, 10)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }

                setOnClickListener {
                    // Only toggle the sections whose first child is a CheckBox
                    container.children
                        .filterIsInstance<LinearLayout>()
                        .filter { it.getChildAt(0) is CheckBox }
                        .forEach { section ->
                            val cb       = section.getChildAt(0) as CheckBox
                            val target   = section.getChildAt(1) as EditText
                            cb.isChecked       = true
                            target.isEnabled   = true
                        }
                    updateStartButtonState()

                }
            })




            addView(Button(this@MainActivity).apply {
                text = "❌ Clear All"
                textSize = 14f
                setTextColor(Color.parseColor("#E53E3E"))
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#FFF5F5"))
                    setStroke(2, Color.parseColor("#E53E3E"))
                }
                setPadding(20, 10, 20, 10)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                setOnClickListener {
                    container.children
                        .filterIsInstance<LinearLayout>()
                        .filter { it.getChildAt(0) is CheckBox }
                        .forEach { section ->
                            val cb       = section.getChildAt(0) as CheckBox
                            val target   = section.getChildAt(1) as EditText
                            cb.isChecked       = false
                            target.isEnabled   = false
                        }
                    updateStartButtonState()

                }
            })
        })

        return container
    }

    // For pending SMS action
    private var pendingSmsAction: (() -> Unit)? = null

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DRIVE_TEST_DATA -> {
                    val data = intent.getStringExtra("data")
                    updateDataDisplay(data ?: "")
                }
                ACTION_DRIVE_TEST_STATUS -> {
                    val status = intent.getStringExtra("status")
                    val isActive = intent.getBooleanExtra("isActive", false)
                    updateDriveTestStatus(status ?: "", isActive)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isMobileDataEnabled()) {
            showMobileDataDialog()
            }
        setupUI()
        attemptAutoSignup()

        // Initialize database
        database = PolariaDatabase.getInstance(this)

        // Register broadcast receivers
        val filter = IntentFilter().apply {
            addAction(ACTION_DRIVE_TEST_DATA)
            addAction(ACTION_DRIVE_TEST_STATUS)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReceiver, filter)

        // Log database stats on startup
        logDatabaseStats()

        // Check if mobile data is enabled
        if (!isMobileDataEnabled()) {
            showMobileDataDialog()
        }

        checkPermissionsAndStart()
    }

    private fun isMobileDataEnabled(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork?.let { network ->
                connectivityManager.getNetworkCapabilities(network)
            }
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
            networkInfo?.isConnected == true
        }
    }

    private fun showMobileDataDialog() {
        // Create main container with gradient background
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
            background = createGradientBackground()
            gravity = Gravity.CENTER
        }

        // Create animated icon container
        val iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                gravity = Gravity.CENTER
                bottomMargin = 32
            }
            background = createCircleBackground(Color.parseColor("#FFF3E0"))
        }

        // Create main icon
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_notify_sync)
            layoutParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.parseColor("#FF9800"))
        }

        // Create signal waves (animated)
        val wave1 = createWaveView(60, Color.parseColor("#FFE0B2"))
        val wave2 = createWaveView(120, Color.parseColor("#FFCC80"))
        val wave3 = createWaveView(180, Color.parseColor("#FFB74D"))

        iconContainer.addView(wave3)
        iconContainer.addView(wave2)
        iconContainer.addView(wave1)
        iconContainer.addView(icon)

        // Create title with custom styling
        val title = TextView(this).apply {
            text = "📱 Mobile Data Required"
            textSize = 24f
            setTextColor(Color.parseColor("#1A1A1A"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        // Create subtitle
        val subtitle = TextView(this).apply {
            text = "Enable mobile data for the best experience"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
        }

        // Create features container
        val featuresLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 48
            }
        }

        // Add feature items
        val features = listOf(
            Pair("🚀", "Real-time data synchronization"),
            Pair("⚡", "Enhanced performance & speed"),
            Pair("🔄", "Automatic updates & latest features"),
            Pair("☁️", "Cloud backup & sync")
        )

        features.forEach { (emoji, text) ->
            featuresLayout.addView(createFeatureItem(emoji, text))
        }

        // Create button container
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create primary button (Settings)
        val settingsBtn = Button(this).apply {
            text = "⚙️  Open Settings"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply {
                bottomMargin = 16
            }
            background = createPrimaryButtonBackground()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8f
            }
        }

        // Create secondary button (Continue)
        val continueBtn = Button(this).apply {
            text = "Continue Without Data"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
            background = createSecondaryButtonBackground()
        }

        // Create and show dialog
        val dialog = AlertDialog.Builder(this)
            .setView(mainLayout)
            .setCancelable(false)
            .create()

        // Add button click listeners with haptic feedback
        settingsBtn.setOnClickListener {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
                    settingsBtn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            } catch (e: Exception) {
                // Haptic feedback not available
            }
            animateButtonPress(settingsBtn) {
                openMobileDataSettings()
                dialog.dismiss()
            }
        }

        continueBtn.setOnClickListener {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
                    continueBtn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            } catch (e: Exception) {
                // Haptic feedback not available
            }
            animateButtonPress(continueBtn) {
                dialog.dismiss()
            }
        }

        // Add animations
        startWaveAnimations(wave1, wave2, wave3)
        startIconPulseAnimation(icon)

        // Assemble layout
        buttonLayout.addView(settingsBtn)
        buttonLayout.addView(continueBtn)

        mainLayout.addView(iconContainer)
        mainLayout.addView(title)
        mainLayout.addView(subtitle)
        mainLayout.addView(featuresLayout)
        mainLayout.addView(buttonLayout)

        // Make dialog background transparent and add entrance animation
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // Add entrance animation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1) {
            mainLayout.apply {
                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    private fun createGradientBackground(): Drawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = 32f
        gradientDrawable.colors = intArrayOf(
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FAFAFA")
        )
        gradientDrawable.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        gradientDrawable.setStroke(2, Color.parseColor("#E0E0E0"))
        return gradientDrawable
    }

    private fun createCircleBackground(color: Int): Drawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.OVAL
        gradientDrawable.setColor(color)
        gradientDrawable.setStroke(4, Color.parseColor("#FFE0B2"))
        return gradientDrawable
    }

    private fun createWaveView(size: Int, color: Int): View {
        return View(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.OVAL
            gradientDrawable.setColor(color)
            background = gradientDrawable
            alpha = 0.4f
        }
    }

    private fun createFeatureItem(emoji: String, text: String): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            setPadding(24, 16, 24, 16)
            background = createFeatureBackground()
        }

        val emojiView = TextView(this).apply {
            this.text = emoji
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 16
            }
        }

        val textView = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#424242"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(emojiView)
        container.addView(textView)
        return container
    }

    private fun createFeatureBackground(): Drawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = 16f
        gradientDrawable.setColor(Color.parseColor("#F8F9FA"))
        gradientDrawable.setStroke(1, Color.parseColor("#E9ECEF"))
        return gradientDrawable
    }

    private fun createPrimaryButtonBackground(): Drawable {
        val normal = GradientDrawable()
        normal.shape = GradientDrawable.RECTANGLE
        normal.cornerRadius = 28f
        normal.colors = intArrayOf(
            Color.parseColor("#2196F3"),
            Color.parseColor("#1976D2")
        )
        normal.orientation = GradientDrawable.Orientation.TOP_BOTTOM

        val pressed = GradientDrawable()
        pressed.shape = GradientDrawable.RECTANGLE
        pressed.cornerRadius = 28f
        pressed.colors = intArrayOf(
            Color.parseColor("#1976D2"),
            Color.parseColor("#1565C0")
        )
        pressed.orientation = GradientDrawable.Orientation.TOP_BOTTOM

        val stateList = StateListDrawable()
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        stateList.addState(intArrayOf(), normal)
        return stateList
    }

    private fun createSecondaryButtonBackground(): Drawable {
        val normal = GradientDrawable()
        normal.shape = GradientDrawable.RECTANGLE
        normal.cornerRadius = 24f
        normal.setColor(Color.TRANSPARENT)
        normal.setStroke(2, Color.parseColor("#E0E0E0"))

        val pressed = GradientDrawable()
        pressed.shape = GradientDrawable.RECTANGLE
        pressed.cornerRadius = 24f
        pressed.setColor(Color.parseColor("#F5F5F5"))
        pressed.setStroke(2, Color.parseColor("#BDBDBD"))

        val stateList = StateListDrawable()
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        stateList.addState(intArrayOf(), normal)
        return stateList
    }

    private fun startWaveAnimations(wave1: View, wave2: View, wave3: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            val waves = listOf(wave1, wave2, wave3)
            val delays = listOf(0L, 300L, 600L)

            waves.forEachIndexed { index, wave ->
                val scaleX = ObjectAnimator.ofFloat(wave, "scaleX", 0.8f, 1.2f, 0.8f)
                val scaleY = ObjectAnimator.ofFloat(wave, "scaleY", 0.8f, 1.2f, 0.8f)
                val alpha = ObjectAnimator.ofFloat(wave, "alpha", 0.3f, 0.7f, 0.3f)

                scaleX.duration = 2000
                scaleX.startDelay = delays[index]
                scaleX.repeatCount = ObjectAnimator.INFINITE
                scaleX.repeatMode = ObjectAnimator.RESTART

                scaleY.duration = 2000
                scaleY.startDelay = delays[index]
                scaleY.repeatCount = ObjectAnimator.INFINITE
                scaleY.repeatMode = ObjectAnimator.RESTART

                alpha.duration = 2000
                alpha.startDelay = delays[index]
                alpha.repeatCount = ObjectAnimator.INFINITE
                alpha.repeatMode = ObjectAnimator.RESTART

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(scaleX, scaleY, alpha)
                animatorSet.start()
            }
        }
    }

    private fun startIconPulseAnimation(icon: ImageView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            val pulse = ObjectAnimator.ofFloat(icon, "alpha", 0.7f, 1f, 0.7f)
            pulse.duration = 1500
            pulse.repeatCount = ObjectAnimator.INFINITE
            pulse.repeatMode = ObjectAnimator.REVERSE
            pulse.start()
        }
    }

    private fun animateButtonPress(button: View, onComplete: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1) {
            button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction(onComplete)
                        .start()
                }
                .start()
        } else {
            onComplete()
        }
    }

    private fun openMobileDataSettings() {
        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
    private fun generateRandomPassword(): String {
        val newPass = (100000..999999).random().toString()
        password = newPass
        return newPass
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Username", text)
        clipboard.setPrimaryClip(clip)

        // Show animated toast
        Toast.makeText(this, "✅ Username copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
    private fun beginDriveTest() {
        // 1) Read user‐entered “targets” from each EditText:
        val smsNumber     = etSmsNumber.text.toString().trim()
        val pingHost      = etPingIp.text.toString().trim()
        val dnsHost       = etDnsDomain.text.toString().trim()
        val httpUploadUrl = etHttpUploadUrl.text.toString().trim()
        val webTestUrl    = etWebTestUrl.text.toString().trim()

        // 2) Build an Intent to start PolarisService
        val intent = Intent(this, PolarisService::class.java).apply {
            action = "START_DRIVE_TEST"

            // Pass the list of enabled tests:
            putStringArrayListExtra("SELECTED_TESTS", ArrayList(selectedTests))

            // Pass each target string as an extra:
            putExtra("SMS_TEST_NUMBER",    smsNumber)
            putExtra("PING_HOST",          pingHost)
            putExtra("DNS_HOST",           dnsHost)
            putExtra("HTTP_UPLOAD_URL",    httpUploadUrl)
            putExtra("WEB_TEST_URL",       webTestUrl)
        }

        // 3) Start the service in the foreground
        ContextCompat.startForegroundService(this, intent)

        // 4) Update UI state in MainActivity
        isDriveTestActive = true
        updateButtonStates(true)
        Toast.makeText(this, "🚗 Drive test launching…", Toast.LENGTH_SHORT).show()
    }

    // helper to convert dp → px
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
    private fun getUniqueDeviceId(): String {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        } catch (e: Exception) {
            "unknown_device_${System.currentTimeMillis()}"
        }
    }
    private fun storeCredentials(username: String, password: String, deviceId: String) {
        val sharedPref = getSharedPreferences("app_credentials", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("username", username)
            putString("password", password)
            putString("device_id", deviceId)
            putBoolean("signed_up", true)
            apply()
        }
    }

    // 3) Update status UI + toast
    private fun updateSignupStatus(message: String, success: Boolean) {
        statusText?.text = if (success) "Authenticated & Ready" else "Authentication Failed"
        statusIcon?.text = if (success) "✅" else "❌"
        statusIndicator?.background = GradientDrawable().apply {
            cornerRadius = 15f
            if (success) {
                setColor(Color.parseColor("#F0FFF4"))
                setStroke(2, Color.parseColor("#68D391"))
            } else {
                setColor(Color.parseColor("#FFF5F5"))
                setStroke(2, Color.parseColor("#F56565"))
            }
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private suspend fun performSignup(
        username: String,
        password: String,
        deviceId: String
    ): SignupResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/auth/signup")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                doInput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            // build JSON
            val jsonPayload = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("device_id", deviceId)
            }

            // send it
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use {
                    it.write(jsonPayload.toString())
                    it.flush()
                }
            }

            // read code + body
            val code = connection.responseCode
            val body = when {
                code == HttpURLConnection.HTTP_OK ||
                        code == HttpURLConnection.HTTP_CREATED -> {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                connection.errorStream != null -> {
                    connection.errorStream.bufferedReader().use { it.readText() }
                }

                else -> ""
            }

            // Parse and decide based on response
            when {
                code == HttpURLConnection.HTTP_OK -> {
                    // 200 means signup was successful (either new signup or already registered)
                    SignupResult.Success
                }

                code == HttpURLConnection.HTTP_CREATED -> {
                    // 201 means new signup was successful
                    SignupResult.Success
                }

                code == HttpURLConnection.HTTP_BAD_REQUEST -> {
                    // Parse JSON error response
                    try {
                        val errorJson = JSONObject(body)
                        val detail = errorJson.optString("detail", "")

                        when {
                            detail.equals("Username already registered", ignoreCase = true) -> {
                                SignupResult.UsernameExists
                            }
                            else -> SignupResult.OtherError
                        }
                    } catch (e: Exception) {
                        // If JSON parsing fails, fallback to string search
                        if (body.contains("Username already registered", ignoreCase = true)) {
                            SignupResult.UsernameExists
                        } else {
                            SignupResult.OtherError
                        }
                    }
                }

                else -> SignupResult.OtherError
            }
        } catch (e: Exception) {
            android.util.Log.e("Signup", "Error during signup", e)
            SignupResult.OtherError
        } finally {
            connection?.disconnect()
        }
    }

    private fun attemptAutoSignup() {
        signupScope.launch {
            try {
                val username = getUniqueUsername()
                val pw = password // your class‐level var
                val deviceId = getUniqueDeviceId()

                val result = performSignup(username, pw, deviceId)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is SignupResult.Success -> {
                            updateSignupStatus("✅ Auto-signup successful!", true)
                            storeCredentials(username, pw, deviceId)
                        }
                        is SignupResult.UsernameExists -> {
                            updateSignupStatus("✅ Account already registered - you're all set!", true)
                            storeCredentials(username, pw, deviceId)
                        }
                        is SignupResult.OtherError -> {
                            updateSignupStatus("❌ Auto-signup failed. Please try again.", false)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateSignupStatus("❌ Signup error: ${e.message}", false)
                }
            }
        }
    }
    private fun changePassword(username: String): Pair<Int, String?> {
        val url = "http://46.249.99.35:8000/auth/change-password-random?username=$username"
        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ByteArray(0)))
            .addHeader("accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val statusCode = response.code
                if (statusCode != 200) return Pair(statusCode, null)

                val body = response.body?.string() ?: return Pair(statusCode, null)
                val json = JSONObject(body)
                return Pair(statusCode, json.getString("new_password"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(-1, null) // -1 for network/exception errors
        }
    }
    private fun isInternetWorking(): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://www.google.com")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun setupUI() {
        val mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }
        statusIndicator = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setColor(Color.parseColor("#F0FFF4"))
                setStroke(2, Color.parseColor("#68D391"))
            }
            setPadding(24, 16, 24, 16)
        }
        // App Header with gradient background
        val headerCard = CardView(this).apply {
            radius = 20f
            cardElevation = 8f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }


        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    Color.parseColor("#667EEA"),
                    Color.parseColor("#764BA2")
                )
                cornerRadius = 20f
            }
            setPadding(32, 40, 32, 40)
            gravity = Gravity.CENTER
        }

        // App icon and title
        val appIcon = TextView(this).apply {
            text = "🌟"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val titleText = TextView(this).apply {
            text = "Polaris Drive Test"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitleText = TextView(this).apply {
            text = "Advanced Cellular Network Analysis"
            textSize = 14f
            setTextColor(Color.parseColor("#E8E8E8"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }





        val userLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Card title
        val userCardTitle = TextView(this).apply {
            text = "🔐 User Credentials"
            textSize = 18f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 24)
        }

        val username = getUniqueUsername()

        // Enhanced User Credentials Card
        val userCard = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }



        // Username section with better styling
        val usernameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.parseColor("#F7FAFC"))
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
            setPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val usernameLabel = TextView(this).apply {
            text = "👤 Username"
            textSize = 12f
            setTextColor(Color.parseColor("#718096"))
            setPadding(0, 0, 0, 8)
        }

        val usernameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val usernameText = TextView(this).apply {
            text = username
            textSize = 16f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val copyButton = TextView(this).apply {
            text = "📋 Copy"
            textSize = 12f
            setTextColor(Color.parseColor("#4299E1"))
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#EBF8FF"))
            }
            setPadding(16, 8, 16, 8)
            isClickable = true
            setOnClickListener {
                copyToClipboard(username)
                // Animate button
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            }
        }

        // Password section with enhanced styling
        val passwordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.parseColor("#F7FAFC"))
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
            setPadding(20, 16, 20, 16)
        }

        val passwordLabel = TextView(this).apply {
            text = "🔑 Password"
            textSize = 12f
            setTextColor(Color.parseColor("#718096"))
            setPadding(0, 0, 0, 8)
        }
        val passwordButton = TextView(this).apply {
            var password = generateRandomPassword()
            text = password
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 12f
                colors = intArrayOf(
                    Color.parseColor("#9F7AEA"),
                    Color.parseColor("#667EEA")
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            setPadding(32, 16, 32, 16)
            gravity = Gravity.CENTER

            setOnClickListener {
                // Check mobile data first
                if (!isMobileDataEnabled()) {
                    showMobileDataDialog()
                    return@setOnClickListener
                }

                // Call endpoint when clicked (only if mobile data is enabled)
                CoroutineScope(Dispatchers.IO).launch {
                    val (statusCode, newPassword) = changePassword(username)
                    runOnUiThread {
                        if (statusCode == 200 && newPassword != null) {
                            text = newPassword
                            // (Optional) trigger your animation here
                        } else {
                            // Check internet connection when there's an error
                            CoroutineScope(Dispatchers.IO).launch {
                                val hasInternet = isInternetWorking()
                                runOnUiThread {
                                    if (hasInternet) {
                                        Toast.makeText(this@MainActivity, "Server has problem and password not changed", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Your connection has problem", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            isClickable = true
        }
        val passwordHint = TextView(this).apply {
            text = "💡 Tap to generate a new secure password"
            textSize = 11f
            setTextColor(Color.parseColor("#A0AEC0"))
            setPadding(0, 8, 0, 0)
            gravity = Gravity.CENTER
        }

        val signUpButton = TextView(this).apply {
            text = "🚀 Sign Up"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 16f
                colors = intArrayOf(
                    Color.parseColor("#48BB78"),
                    Color.parseColor("#38A169")
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            setPadding(40, 20, 40, 20)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 24, 0, 0)
            }

            setOnClickListener {
                // Animate button press
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
                if (!isMobileDataEnabled()) {
                    showMobileDataDialog()
                    return@setOnClickListener
                }
                // Call the signup function
                attemptAutoSignup()
            }
            isClickable = true
        }
// Enhanced Interval Configuration Card
        val intervalCard = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }

        val intervalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val intervalTitle = TextView(this).apply {
            text = "⏱️Interval Configuration"
            textSize = 16f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        }

        val intervalDescription = TextView(this).apply {
            text = "Configure how frequently data is collected during your drive test"
            textSize = 15f
            setTextColor(Color.parseColor("#718096"))
            setPadding(0, 0, 0, 24)
        }

        // Custom interval input container with gradient background
        val intervalInputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f
                colors = intArrayOf(
                    Color.parseColor("#EDF2F7"),
                    Color.parseColor("#F7FAFC")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(1, Color.parseColor("#E2E8F0"))
            }
            setPadding(24, 20, 24, 20)
        }

        val intervalLabel = TextView(this).apply {
            text = "📊 Interval(sec)"
            textSize = 14f
            setTextColor(Color.parseColor("#4A5568"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 12)
        }

        // Interactive interval selector with buttons
        val intervalSelectorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }


        etIntervalSeconds = EditText(this).apply {
            hint = "Seconds"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#2D3748"))
            setHintTextColor(Color.parseColor("#A0AEC0"))
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.WHITE)
                setStroke(2, Color.parseColor("#667EEA"))
            }
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(16, 0, 16, 0)
            }
            // default value
            setText("5")

            // Add focus change listener for enhanced styling
            setOnFocusChangeListener { _, hasFocus ->
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    if (hasFocus) {
                        setColor(Color.parseColor("#EBF8FF"))
                        setStroke(3, Color.parseColor("#4299E1"))
                    } else {
                        setColor(Color.WHITE)
                        setStroke(2, Color.parseColor("#667EEA"))
                    }
                }
            }
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager



        val increaseBtn = TextView(this).apply {
            text = "+"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)

            // 1) remove default font padding
            includeFontPadding = false

            // 2) center text
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER

            // 3) remove any extra padding
            setPadding(0, 0, 0, 0)

            // 4) size in dp so it scales correctly
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            )

            // 5) a true oval gradient background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setColors(intArrayOf(
                    Color.parseColor("#68D391"),
                    Color.parseColor("#48BB78")
                ))
            }

            isClickable = true
        }
        increaseBtn.setOnClickListener {

            audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)

            // Read current interval (fallback to 5)
            val currentValue = etIntervalSeconds.text.toString().toIntOrNull() ?: 5

            // You can enforce a max if you like; here we just increment
            etIntervalSeconds.setText((currentValue + 1).toString())

            // Animate the “press” effect
            increaseBtn.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setDuration(100)
                .withEndAction {
                    increaseBtn.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
        val decreaseBtn = TextView(this).apply {
            text = "–"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)

            // Remove default font padding so the “–” is centered vertically
            includeFontPadding = false

            // Center the text
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER

            // Clear any extra padding
            setPadding(0, 0, 0, 0)

            // Fixed size in dp
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            )

            // True oval gradient background
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setColors(intArrayOf(
                    Color.parseColor("#E53E3E"),
                    Color.parseColor("#C53030")
                ))
            }

            isClickable = true
        }

// Then your click‐listener:
        decreaseBtn.setOnClickListener {
            val currentValue = etIntervalSeconds.text.toString().toIntOrNull() ?: 5
            if (currentValue > 1) {
                etIntervalSeconds.setText((currentValue - 1).toString())
                // Animate button press
                decreaseBtn.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction {
                        decreaseBtn.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
        }
        // Quick preset buttons
        val presetButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
            gravity = Gravity.CENTER
        }

        val presetValues = listOf(1, 3, 5, 10, 15)
        presetValues.forEach { value ->
            val presetBtn = TextView(this).apply {
                text = "${value}s"
                textSize = 12f
                setTextColor(Color.parseColor("#667EEA"))
                background = GradientDrawable().apply {
                    cornerRadius = 15f
                    setColor(Color.parseColor("#F0F4FF"))
                    setStroke(1, Color.parseColor("#C6D2FD"))
                }
                setPadding(12, 6, 12, 6)
                gravity = Gravity.CENTER
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4, 0, 4, 0)
                }

                setOnClickListener {
                    etIntervalSeconds.setText(value.toString())
                    // Animate selection
                    val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.2f, 1f)
                    val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.2f, 1f)
                    AnimatorSet().apply {
                        playTogether(scaleX, scaleY)
                        duration = 200
                        start()
                    }
                }
            }
            presetButtonsRow.addView(presetBtn)
        }

        val intervalHint = TextView(this).apply {
            text = "💡 Recommended: 3-5 seconds for optimal performance"
            textSize = 12f
            setTextColor(Color.parseColor("#A0AEC0"))
            setPadding(0, 12, 0, 0)
            gravity = Gravity.CENTER
        }

        passwordContainer.addView(passwordLabel)
        passwordContainer.addView(passwordButton)
        passwordContainer.addView(passwordHint)

        usernameRow.addView(usernameText)
        usernameRow.addView(copyButton)
        usernameContainer.addView(usernameLabel)
        usernameContainer.addView(usernameRow)
        usernameContainer.addView(signUpButton)


        headerLayout.addView(appIcon)
        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)
        headerCard.addView(headerLayout)
        mainLayout.addView(headerCard)

        userLayout.addView(userCardTitle)
        userLayout.addView(usernameContainer)
        userLayout.addView(passwordContainer)
        userCard.addView(userLayout)
        mainLayout.addView(userCard)











        // Enhanced Status Card
        statusCard = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }

        val statusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val statusTitle = TextView(this).apply {
            text = "📊 Drive Test Status"
            textSize = 18f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        }

        // Status indicator with icon
        val statusIndicator = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 15f
                setColor(Color.parseColor("#F0FFF4"))
                setStroke(2, Color.parseColor("#68D391"))
            }
            setPadding(24, 16, 24, 16)
        }

        statusIcon = TextView(this).apply {
            text = "⚪"
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }

        statusText = TextView(this).apply {
            text = "Ready to Start"
            textSize = 16f
            setTextColor(Color.parseColor("#38A169"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Connection status and data count
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        connectionStatusText = TextView(this).apply {
            text = "📶 Network: Connected"
            textSize = 12f
            setTextColor(Color.parseColor("#4A5568"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        dataCountText = TextView(this).apply {
            text = "📊 Samples: 0"
            textSize = 12f
            setTextColor(Color.parseColor("#4A5568"))
            gravity = Gravity.END
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                setMargins(0, 12, 0, 0)
            }
            progress = 0
            max = 100
            progressDrawable = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.progress_horizontal)
            visibility = android.view.View.GONE
        }
        testSelectionContainer = createTestSelectionUI()
        mainLayout.addView(testSelectionContainer)
        statsRow.addView(connectionStatusText)
        statsRow.addView(dataCountText)
        statusIndicator.addView(statusIcon)
        statusIndicator.addView(statusText)
        statusLayout.addView(statusTitle)
        statusLayout.addView(statusIndicator)
        statusLayout.addView(statsRow)
        statusLayout.addView(progressBar)
        statusCard.addView(statusLayout)
        mainLayout.addView(statusCard)

        // Build the interval selector
        intervalSelectorRow.addView(decreaseBtn)
        intervalSelectorRow.addView(etIntervalSeconds)
        intervalSelectorRow.addView(increaseBtn)

        intervalInputContainer.addView(intervalLabel)
        intervalInputContainer.addView(intervalSelectorRow)
        intervalInputContainer.addView(presetButtonsRow)
        intervalInputContainer.addView(intervalHint)

        intervalLayout.addView(intervalTitle)
        intervalLayout.addView(intervalDescription)
        intervalLayout.addView(intervalInputContainer)
        intervalCard.addView(intervalLayout)
        mainLayout.addView(intervalCard)

        // Enhanced Control Buttons
        val buttonCard = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }



        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL  // Set this first
            setPadding(32, 32, 32, 32)
        }

        val horizontalButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        startDriveTestBtn = Button(this).apply {
            text = "🚗 Start Drive Test"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 25f
                colors = intArrayOf(
                    Color.parseColor("#48BB78"),
                    Color.parseColor("#38A169")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            setPadding(32, 20, 32, 20)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 12, 0)
            }
            setOnClickListener {
                updateStartButtonState()
                checkAndRequestSmsPermission {
                    startDriveTest()
                }
            }
        }

        finishDriveTestBtn = Button(this).apply {
            text = "🏁 Finish Test"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 25f
                colors = intArrayOf(
                    Color.parseColor("#F56565"),
                    Color.parseColor("#E53E3E")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            setPadding(32, 20, 32, 20)
            isEnabled = false
            alpha = 0.5f
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(12, 0, 0, 0)
            }
            setOnClickListener { finishDriveTest() }
        }

        exportButton = Button(this).apply {
            text = "📤 Export Data"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 25f
                colors = intArrayOf(
                    Color.parseColor("#667EEA"),
                    Color.parseColor("#764BA2")
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            setOnClickListener {
                if (!isMobileDataEnabled()) {
                    showMobileDataDialog()
                    return@setOnClickListener
                }
                checkStoragePermissionAndExport()
                exportDataToServer()
            }
        }

// Add the buttons to the horizontal row
        horizontalButtonRow.addView(startDriveTestBtn)
        horizontalButtonRow.addView(finishDriveTestBtn)

// Add both the horizontal row and export button to the main vertical layout
        buttonLayout.addView(horizontalButtonRow)
        buttonLayout.addView(exportButton)

// Then add buttonLayout to your buttonCard as before
        buttonCard.addView(buttonLayout)
        mainLayout.addView(buttonCard)



        // Enhanced Data Display Card
        val dataCard = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val dataLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val dataTitle = TextView(this).apply {
            text = "📡 Live Cellular Data Stream"
            textSize = 18f
            setTextColor(Color.parseColor("#2D3748"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        }

        dataDisplay = TextView(this).apply {
            text = """
                🌟 Welcome to Polaris Drive Test!
                
                📋 Instructions:
                • Tap 'Start Drive Test' to begin data collection
                • Drive around to collect cellular network data
                • Real-time data will appear here
                • Tap 'Finish Test' when done
                
                📊 Features:
                • Network technology detection (4G/5G)
                • Signal strength monitoring
                • Operator identification
                • GPS location tracking
                
                Ready to start your network analysis journey? 🚀
            """.trimIndent()
            textSize = 14f
            setTextColor(Color.parseColor("#4A5568"))
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.parseColor("#F7FAFC"))
                setStroke(1, Color.parseColor("#E2E8F0"))
            }
            setPadding(20, 20, 20, 20)
        }

        dataScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(dataDisplay)
        }

        dataLayout.addView(dataTitle)
        dataLayout.addView(dataScrollView)
        dataCard.addView(dataLayout)
        mainLayout.addView(dataCard)

        mainScrollView.addView(mainLayout)
        setContentView(mainScrollView)
    }

    private fun getUniqueUsername(): String {
        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId
    }

    private fun checkAndRequestSmsPermission(whenGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSmsAction = whenGranted
            requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST)
        } else {
            whenGranted()
        }
    }

    private fun startDriveTest() {
        if (!isDriveTestActive) {
            // 1) Read the five target strings
            val smsNumber     = etSmsNumber.text.toString().trim()
            val pingHost      = etPingIp.text.toString().trim()
            val dnsHost       = etDnsDomain.text.toString().trim()
            val httpUploadUrl = etHttpUploadUrl.text.toString().trim()
            val webTestUrl    = etWebTestUrl.text.toString().trim()
            val intervalSec = etIntervalSeconds.text.toString().toLongOrNull() ?: 5L

            // 2) Build Intent with ALL extras (just like your beginDriveTest() did)
            val intent = Intent(this, PolarisService::class.java).apply {
                action = "START_DRIVE_TEST"
                putStringArrayListExtra("SELECTED_TESTS", ArrayList(selectedTests))
                putExtra("SMS_TEST_NUMBER",    smsNumber)
                putExtra("PING_HOST",          pingHost)
                putExtra("DNS_HOST",           dnsHost)
                putExtra("HTTP_UPLOAD_URL",    httpUploadUrl)
                putExtra("WEB_TEST_URL",       webTestUrl)
                putExtra("LOOP_INTERVAL_MS", intervalSec * 1_000L)

            }

            // 3) Launch the foreground service
            ContextCompat.startForegroundService(this, intent)

            // 4) Update UI state
            isDriveTestActive = true
            dataCount = 0
            updateButtonStates(true)
            updateStatusDisplay("🔴 Drive Test Active", Color.parseColor("#E53E3E"), "🔴")
            progressBar.visibility = android.view.View.VISIBLE
            startProgressAnimation()

            val testsText = selectedTests.joinToString(", ")
            Toast.makeText(this, "🚗 Drive test started with: $testsText", Toast.LENGTH_LONG).show()

            dataDisplay.text = """
            🔴 DRIVE TEST ACTIVE
            
            📊 Running Tests: $testsText
            🚗 Drive around to capture network information
            📡 Real-time data will appear below
            
            ⏱️ Test started: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            
            ━━━━━━━━━━━━━ LIVE DATA ━━━━━━━━━━━━━
        """.trimIndent()
        }
    }

    private fun finishDriveTest() {
        if (isDriveTestActive) {
            val intent = Intent(this, PolarisService::class.java).apply {
                action = "STOP_DRIVE_TEST"
            }
            startService(intent)

            isDriveTestActive = false

            // Update UI with animations
            updateButtonStates(false)
            updateStatusDisplay("✅ Test Completed", Color.parseColor("#38A169"), "✅")

            progressBar.visibility = android.view.View.GONE

            Toast.makeText(this, "🏁 Drive test completed successfully!", Toast.LENGTH_SHORT).show()
            showDriveTestSummary()
        }
    }

    private fun updateButtonStates(testActive: Boolean) {
        startDriveTestBtn.isEnabled = !testActive
        finishDriveTestBtn.isEnabled = testActive

        // Animate button states
        if (testActive) {
            startDriveTestBtn.animate().alpha(0.5f).setDuration(300).start()
            finishDriveTestBtn.animate().alpha(1f).setDuration(300).start()
        } else {
            startDriveTestBtn.animate().alpha(1f).setDuration(300).start()
            finishDriveTestBtn.animate().alpha(0.5f).setDuration(300).start()
        }
    }

    private fun updateStatusDisplay(text: String, color: Int, icon: String) {
        statusText.text = text
        statusText.setTextColor(color)
        statusIcon.text = icon

        // Update status indicator background using stored reference
        val bgColor = when {
            text.contains("Active") -> Color.parseColor("#FFF5F5")
            text.contains("Completed") -> Color.parseColor("#F0FFF4")
            else -> Color.parseColor("#F7FAFC")
        }

        val strokeColor = when {
            text.contains("Active") -> Color.parseColor("#FC8181")
            text.contains("Completed") -> Color.parseColor("#68D391")
            else -> Color.parseColor("#E2E8F0")
        }

        statusIndicator.background = GradientDrawable().apply {
            cornerRadius = 15f
            setColor(bgColor)
            setStroke(2, strokeColor)
        }
    }

    private fun startProgressAnimation() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isDriveTestActive) {
                    val progress = (Math.random() * 30 + 10).toInt()
                    progressBar.progress = progress
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun updateDataDisplay(data: String) {
        runOnUiThread {
            dataCount++
            dataCountText.text = "📊 Samples: $dataCount"

            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

            val formattedData = buildString {
                appendLine("⏰ $timestamp")
                appendLine("$data")
                appendLine("━".repeat(40))
                appendLine()
                append(dataDisplay.text)
            }

            dataDisplay.text = formattedData

            // Auto-scroll to top to show latest data
            dataScrollView.post {
                dataScrollView.scrollTo(0, 0)
            }
        }
    }

    private fun updateDriveTestStatus(status: String, isActive: Boolean) {
        runOnUiThread {
            isDriveTestActive = isActive

            if (isActive) {
                updateStatusDisplay("🔴 Drive Test Active", Color.parseColor("#E53E3E"), "🔴")
                updateButtonStates(true)
            } else {
                updateStatusDisplay("✅ Test Completed", Color.parseColor("#38A169"), "✅")
                updateButtonStates(false)
            }
        }
    }


    private fun showDriveTestSummary() {
        lifecycleScope.launch {
            try {

                // Get ALL merged entries (cellular + network tests)
                val allData = database.cellularNetworkDataDao().getAllEntries()

                // If you want to show recent data for other analysis, get it separately
                val recentData = allData.take(50)

                val summary = buildString {
                    appendLine("🎉 DRIVE TEST COMPLETED!")
                    appendLine()
                    appendLine("📊 Test Summary:")
                    // Use allData.size for the correct total count
                    appendLine("   • Total samples collected: ${allData.size}")
                    appendLine("   • Test duration: ${getTestDuration()}")
                    appendLine()

                    // Use allData for accurate technology breakdown
                    val technologies = allData.groupBy { it.technology }
                    appendLine("📶 Network Technologies:")
                    technologies.forEach { (tech, data) ->
                        appendLine("   • $tech: ${data.size} samples")
                    }
                    appendLine()

                    // Use allData for accurate operator breakdown
                    val operators = allData.groupBy { it.operatorName }
                    appendLine("🌐 Network Operators:")
                    operators.forEach { (op, data) ->
                        appendLine("   • $op: ${data.size} samples")
                    }
                    appendLine()

                    appendLine("✨ Thank you for using Polaris!")
                    appendLine("Your network data has been saved for analysis.")
                }

                runOnUiThread {
                    dataDisplay.text = summary
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating summary", e)
            }
        }
    }
    private fun getTestDuration(): String {
        // This would ideally track actual test start time
        return "Session completed"
    }

    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasPermissions(this)) {
            checkGpsAndStartService()
        }
        logDatabaseStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        signupJob.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver)
        Log.i(TAG, "MainActivity destroyed")
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkGpsAndStartService()
                } else {
                    Toast.makeText(
                        this,
                        "⚠️ All permissions are required for optimal functionality",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingSmsAction?.invoke()
                } else {
                    Toast.makeText(
                        this,
                        "📱 SMS permission is required for network tests",
                        Toast.LENGTH_LONG
                    ).show()
                }
                pendingSmsAction = null
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportDataToJsonl()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission is required to export data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }
    }

    private fun checkPermissionsAndStart() {
        if (PermissionUtils.hasPermissions(this)) {
            checkGpsAndStartService()
        } else {
            PermissionUtils.requestPermissions(this, PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkGpsAndStartService() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            Toast.makeText(this, "📍 Please enable GPS for accurate location tracking", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            connectionStatusText.text = "📶 Network: Connected • GPS: Active"
        }
    }

    private fun logDatabaseStats() {
        lifecycleScope.launch {
            try {
                val locationCount = database.locationDao().getCount()
                val cellularCount = database.cellularNetworkDataDao().getRecordCount()
                val technologies = database.cellularNetworkDataDao().getAllTechnologies()
                val operators = database.cellularNetworkDataDao().getAllOperators()
                Log.i(TAG, "📊 Database Statistics:")
                Log.i(TAG, "   └── Location records: $locationCount")
                Log.i(TAG, "   └── Cellular records: $cellularCount")
                Log.i(TAG, "   └── Technologies: ${technologies.joinToString(", ")}")
                Log.i(TAG, "   └── Operators: ${operators.joinToString(", ")}")

                runOnUiThread {
                    if (cellularCount > 0) {
                        dataCountText.text = "📊 Total Records: $cellularCount"
                        Toast.makeText(
                            this@MainActivity,
                            "📊 Database loaded: $cellularCount records available",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading database statistics", e)
                runOnUiThread {
                    connectionStatusText.text = "📶 Network: Connected • Database: Error"
                }
            }
        }
    }

    private fun exportDataToServer() {
        lifecycleScope.launch {
            try {
                showExportProgress(true)
                val allData = withContext(Dispatchers.IO) {
                    // Fetch all entries from your database
                    database.cellularNetworkDataDao().getAllEntries()
                }
                if (allData.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    showExportProgress(false)
                    return@launch
                }

                // Convert allData to CellularDataExport list
                val exportData = allData.map { entity ->
                    CellularDataExport(
                        id = entity.id,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        timestamp = entity.timestamp,
                        technology = entity.technology,
                        plmnId = entity.plmnId,
                        lac = entity.lac,
                        rac = entity.rac,
                        tac = entity.tac,
                        cellId = entity.cellId,
                        frequencyBand = entity.frequencyBand,
                        arfcn = entity.arfcn,
                        actualFrequency = entity.actualFrequency,
                        rsrp = entity.rsrp,
                        rsrq = entity.rsrq,
                        rscp = entity.rscp,
                        ecNo = entity.ecNo,
                        rxLev = entity.rxLev,
                        sinr = entity.sinr,
                        operatorName = entity.operatorName,
                        notes = entity.notes,
                        httpUploadRate = entity.httpUploadRate,
                        pingResponseTime = entity.pingResponseTime,
                        dnsResponseTime = entity.dnsResponseTime,
                        webResponseTime = entity.webResponseTime,
                        smsDeliveryTime = entity.smsDeliveryTime,
                        testNotes = entity.testNotes,
                        deviceId = entity.deviceId,
                        timestampFormatted = formatTimestamp(entity.timestamp)
                    )
                }

                // Send all records in a single request
                val success = withContext(Dispatchers.IO) {
                    exportAllDataToServer(exportData)
                }

                showExportProgress(false)
                if (success) {
                    Toast.makeText(
                        this@MainActivity,
                        "Successfully exported ${exportData.size} records to server.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to export data to server.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showExportProgress(false)
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }

    suspend fun exportAllDataToServer(dataList: List<CellularDataExport>): Boolean {
        val client = OkHttpClient()
        val gson = Gson()
        val url = "http://46.249.99.35:8000/drive-data/app"
        val json = gson.toJson(dataList) // Convert the entire list to JSON
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    /**
     * Export all cellular network data to a JSONL file
     */
    private fun exportDataToJsonl() {
        lifecycleScope.launch {
            try {
                showExportProgress(true)

                // Get all data from database
                val allData = withContext(Dispatchers.IO) {
                    // Assuming you have access to your DAO instance
                    // Replace 'cellularDao' with your actual DAO instance
                    database.cellularNetworkDataDao().getAllEntries()
                }

                if (allData.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    showExportProgress(false)
                    return@launch
                }

                // Convert to export format
                val exportData = allData.map { entity ->
                    CellularDataExport(
                        id = entity.id,
                        deviceId = entity.deviceId,
                        timestamp = entity.timestamp,
                        timestampFormatted = formatTimestamp(entity.timestamp),
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        technology = entity.technology,
                        plmnId = entity.plmnId,
                        lac = entity.lac,
                        rac = entity.rac,
                        tac = entity.tac,
                        cellId = entity.cellId,
                        frequencyBand = entity.frequencyBand,
                        arfcn = entity.arfcn,
                        actualFrequency = entity.actualFrequency,
                        rsrp = entity.rsrp,
                        rsrq = entity.rsrq,
                        rscp = entity.rscp,
                        ecNo = entity.ecNo,
                        rxLev = entity.rxLev,
                        sinr = entity.sinr,
                        operatorName = entity.operatorName,
                        notes = entity.notes,
                        httpUploadRate = entity.httpUploadRate,
                        pingResponseTime = entity.pingResponseTime,
                        dnsResponseTime = entity.dnsResponseTime,
                        webResponseTime = entity.webResponseTime,
                        smsDeliveryTime = entity.smsDeliveryTime,
                        testNotes = entity.testNotes
                    )
                }

                // Create JSONL file
                val fileName = withContext(Dispatchers.IO) {
                    createJsonlFile(exportData)
                }

                showExportProgress(false)
                Toast.makeText(
                    this@MainActivity,
                    "Data exported successfully!\nFile: $fileName\nRecords: ${exportData.size}",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                showExportProgress(false)
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }

    /**
     * Create JSONL file with the cellular data
     */
    private suspend fun createJsonlFile(data: List<CellularDataExport>): String = withContext(Dispatchers.IO) {
        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        // Create filename with timestamp
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "polaris_cellular_data_$timestamp.jsonl"

        // Get Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)

        FileWriter(file).use { writer ->
            data.forEach { record ->
                val jsonLine = gson.toJson(record)
                writer.write(jsonLine + "\n")
            }
        }

        return@withContext fileName
    }

    /**
     * Format timestamp to human-readable format
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Show/hide export progress (you can customize this based on your UI)
     */
    private fun showExportProgress(show: Boolean) {
        // You can show a progress dialog, change button text, etc.
        // For example, if you have a export button:
        exportButton?.apply {
            isEnabled = !show
            text = if (show) "Exporting..." else "📤 Export Data"
        }
    }
    private fun checkStoragePermissionAndExport() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ doesn't need WRITE_EXTERNAL_STORAGE for Downloads folder
            exportDataToJsonl()
        } else {
            // For older Android versions, check permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                exportDataToJsonl()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

}

sealed class SignupResult {
    object Success         : SignupResult()
    object UsernameExists  : SignupResult()
    object OtherError      : SignupResult()
}