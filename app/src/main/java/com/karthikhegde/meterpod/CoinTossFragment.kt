package com.karthikhegde.meterpod

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

        coinView.onFlipComplete = { headsResult ->
            resultText.text = if (headsResult) "Heads!" else "Tails!"
            if (headsResult) headsCount++ else tailsCount++
            updateTally()
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

    private fun updateTally() {
        tallyText.text = "Heads: $headsCount   Tails: $tailsCount"
    }
}
