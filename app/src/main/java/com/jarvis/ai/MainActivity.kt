
package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : Activity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    private val microphoneRequestCode = 100

    private val aiScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val model by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Firebase.initialize(context = this)

            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "Jarvis",
                "Firebase başlatma hatası",
                e
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val titleText = TextView(this).apply {
            text = "JARVIS IS ONLINE"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Hazırım."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        resultText = TextView(this).apply {
            text = "Komutunu bekliyorum."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val microphoneButton = Button(this).apply {
            text = "KONUŞ"
            setOnClickListener {
                startListening()
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(resultText)
        layout.addView(microphoneButton)

        setContentView(layout)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageResult =
                    textToSpeech?.setLanguage(Locale("tr", "TR"))

                ttsReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
                    languageResult != null &&
                    languageResult >= TextToSpeech.LANG_AVAILABLE

                if (!ttsReady) {
                    statusText.text =
                        "Türkçe ses paketi bulunamadı."
                }
            } else {
                statusText.text =
                    "Ses motoru başlatılamadı."
            }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text =
                "Konuşma tanıma servisi bulunamadı."
        } else {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            setupSpeechRecognizer()
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "Seni dinliyorum..."
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Konuşmanı algılıyorum..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText.text = "Gemini düşünüyor..."
                }

                override fun onError(error: Int) {
                    statusText.text =
                        "Ses algılanamadı. Tekrar dene. ($error)"
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val spokenText = matches?.firstOrNull()

                    if (spokenText != null) {
                        resultText.text = spokenText
                        askGemini(spokenText)
                    } else {
                        statusText.text =
                            "Konuşma anlaşılamadı."
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun askGemini(question: String) {
        statusText.text = "Gemini düşünüyor..."

        aiScope.launch {
            try {
                val response = model.generateContent(
                    "Sen Jarvis adlı Türkçe konuşan bir " +
                    "kişisel asistansın. Açık, doğal ve " +
                    "yardımcı cevaplar ver. Kullanıcının " +
                    "sorusu: $question"
                )

                val reply = response.text?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Bu soruya şu anda cevap üretemedim."

                resultText.text = reply
                statusText.text = "Cevap hazır."

                if (ttsReady) {
                    textToSpeech?.speak(
                        reply,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "jarvis_reply"
                    )
                }
            } catch (e: Exception) {
                statusText.text =
                    "Gemini bağlantısında hata oluştu."

                android.util.Log.e(
                    "Jarvis",
                    "Gemini hatası",
                    e
                )
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                microphoneRequestCode
            )
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "tr-TR"
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "tr-TR"
            )
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            statusText.text = "Dinleme başlatılamadı."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == microphoneRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else if (requestCode == microphoneRequestCode) {
            statusText.text = "Mikrofon izni verilmedi."
        }
    }

    override fun onDestroy() {
        aiScope.cancel()

        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
