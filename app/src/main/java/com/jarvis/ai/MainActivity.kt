package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    private val microphoneRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "Komutun burada görünecek."
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

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Konuşma tanıma servisi bulunamadı."
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupSpeechRecognizer()
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Seni dinliyorum..."
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "Konuşmanı algılıyorum..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                statusText.text = "İşleniyor..."
            }

            override fun onError(error: Int) {
                statusText.text = "Ses algılanamadı. Tekrar dene. ($error)"
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                resultText.text = matches?.firstOrNull()
                    ?: "Konuşma anlaşılamadı."

                statusText.text = "Dinleme tamamlandı."
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}