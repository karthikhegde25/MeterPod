package com.karthikhegde.meterpod

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Word & character counter. Distinguishes several character-related
 * figures on purpose, since they answer different questions:
 *  - Words: counted as maximal runs of English letters (regex [A-Za-z]+).
 *    A token like "hello," still counts (the comma is just a non-letter
 *    boundary), but "123" or "!!!" don't count as words at all, and
 *    something like "test123" counts as one word ("test") since the
 *    digits aren't part of any letter run.
 *  - Characters: every character in the text, spaces/punctuation/digits included.
 *  - Characters (no spaces): the same, minus whitespace.
 *  - English letters: only a-z/A-Z - i.e. actual alphabetic letters, with
 *    digits, spaces, and symbols like "?", "!", "," all excluded.
 */
class WordCounterFragment : Fragment() {

    private val wordPattern = Regex("[A-Za-z]+")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_word_counter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textInput = view.findViewById<EditText>(R.id.textInput)
        val wordsCountText = view.findViewById<TextView>(R.id.wordsCountText)
        val charsCountText = view.findViewById<TextView>(R.id.charsCountText)
        val charsNoSpacesCountText = view.findViewById<TextView>(R.id.charsNoSpacesCountText)
        val englishLettersCountText = view.findViewById<TextView>(R.id.englishLettersCountText)

        fun updateCounts(text: String) {
            val wordCount = wordPattern.findAll(text).count()
            val charCount = text.length
            val charNoSpaceCount = text.count { !it.isWhitespace() }
            val englishLetterCount = text.count { it in 'a'..'z' || it in 'A'..'Z' }

            wordsCountText.text = String.format("%,d", wordCount)
            charsCountText.text = String.format("%,d", charCount)
            charsNoSpacesCountText.text = String.format("%,d", charNoSpaceCount)
            englishLettersCountText.text = String.format("%,d", englishLetterCount)
        }

        textInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCounts(s?.toString().orEmpty())
            }
        })

        view.findViewById<Button>(R.id.clearTextButton).setOnClickListener {
            textInput.setText("")
        }

        updateCounts("")
    }
}
