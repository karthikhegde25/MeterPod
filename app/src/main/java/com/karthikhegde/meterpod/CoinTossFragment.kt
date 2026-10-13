package com.karthikhegde.meterpod

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.CoinView
import kotlin.random.Random

class CoinTossFragment : Fragment() {

    private lateinit var coinView: CoinView
    private lateinit var resultText: TextView
    private lateinit var tallyText: TextView

    private var headsCount = 0
    private var tailsCount = 0

    private var soundPool: SoundPool? = null
    private var landSoundId = 0
    private var landSoundLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_coin_toss, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coinView = view.findViewById(R.id.coinView)
        resultText = view.findViewById(R.id.resultText)
        tallyText = view.findViewById(R.id.tallyText)

        setupSoundPool()

        coinView.onFlipComplete = { headsResult ->
            resultText.text = if (headsResult) "Heads!" else "Tails!"
            if (headsResult) headsCount++ else tailsCount++
            updateTally()
            playLandSound()
        }

        coinView.setOnClickListener {
            resultText.text = "Tossing…"
            coinView.toss(Random.nextBoolean())
        }

        view.findViewById<Button>(R.id.resetTallyButton).setOnClickListener {
            headsCount = 0
            tailsCount = 0
            updateTally()
        }

        updateTally()
    }

    private fun setupSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        landSoundLoaded = false
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == landSoundId) landSoundLoaded = true
        }
        landSoundId = soundPool?.load(requireContext(), R.raw.coin_land, 1) ?: 0
    }

    private fun playLandSound() {
        if (landSoundLoaded) {
            soundPool?.play(landSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        soundPool?.release()
        soundPool = null
    }

    private fun updateTally() {
        tallyText.text = "Heads: $headsCount   Tails: $tailsCount"
    }
}
