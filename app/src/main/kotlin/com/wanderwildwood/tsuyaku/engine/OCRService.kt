/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.translator

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_PARA
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.math.min
import kotlin.system.measureTimeMillis

data class TextLine(
  var text: String,
  var boundingBox: Rect,
  var wordRects: Array<Rect>,
)

data class TextBlock(
  val lines: Array<TextLine>,
)

data class WordInfo(
  val text: String,
  val confidence: Float,
  val boundingBox: Rect,
  val isFirstInLine: Boolean,
  var isLastInLine: Boolean,
  var isLastInPara: Boolean,
)

fun getSentences(
  bitmap: Bitmap,
  tessInstance: TessBaseAPI,
  minConfidence: Int = 75,
): Array<TextBlock> {
  tessInstance.setImage(bitmap)
  tessInstance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD)

  // calling utf8text is required to create iterator
  tessInstance.utF8Text

  val iter = tessInstance.resultIterator
  if (iter == null) {
    Log.e("OCRService", "Got Null iterator from Tesseract")
    return emptyArray()
  }

  iter.begin()
  val allWords = mutableListOf<WordInfo>()
  do {
    val word = iter.getUTF8Text(RIL_WORD)
    if (word == null) {
      Log.e("OCRService", "WTF word was null")
      continue
    }

    allWords.add(
      WordInfo(
        text = word,
        confidence = iter.confidence(RIL_WORD),
        boundingBox = iter.getBoundingRect(RIL_WORD),
        isFirstInLine = iter.isAtBeginningOf(RIL_TEXTLINE),
        isLastInLine = iter.isAtFinalElement(RIL_TEXTLINE, RIL_WORD),
        isLastInPara = iter.isAtFinalElement(RIL_PARA, RIL_WORD),
      ),
    )
  } while (iter.next(RIL_WORD))

  iter.delete()
  tessInstance.clear()

  val filteredWords = mutableListOf<WordInfo>()
  var pendingFirstInLine = false

  for (i in allWords.indices) {
    val wordInfo = allWords[i]

    val shouldInclude =
      wordInfo.confidence >= minConfidence &&
        !(wordInfo.text.length == 1 && wordInfo.confidence < min(100, minConfidence + 5))

    if (shouldInclude) {
      val adjustedWordInfo =
        wordInfo.copy(
          // If we skipped over the previous word (due to low confidence)
          // then we drag over the 'first' marker
          isFirstInLine = wordInfo.isFirstInLine || pendingFirstInLine,
          isLastInLine = wordInfo.isLastInLine,
          isLastInPara = wordInfo.isLastInPara,
        )
      filteredWords.add(adjustedWordInfo)
      pendingFirstInLine = false
    } else {
      if (wordInfo.isFirstInLine) {
        pendingFirstInLine = true
      }
      // if we are skipping the last word; add the 'last' marker to the previous (kept) word
      if (wordInfo.isLastInLine && i > 0) {
        allWords[i - 1].isLastInLine = true
      }
      if (wordInfo.isLastInPara && i > 0) {
        allWords[i - 1].isLastInPara = true
      }
    }
  }

  val blocks = mutableListOf<TextBlock>()
  val lines = mutableListOf<TextLine>()
  var line = TextLine("", Rect(0, 0, 0, 0), emptyArray())
  var lastRight = 0

  for (wordInfo in filteredWords) {
    val word = wordInfo.text
    if (word.trim() == "") {
      continue
    }
    val boundingBox = wordInfo.boundingBox
    val skippedFirstWord = boundingBox.right < line.boundingBox.left
    val firstWordInLine = wordInfo.isFirstInLine || skippedFirstWord
    val lastWordInLine = wordInfo.isLastInLine
    val lastWordInPara = wordInfo.isLastInPara

    if (firstWordInLine) {
      line = TextLine(word, boundingBox, arrayOf(boundingBox))
    } else {
      val delta = boundingBox.left - lastRight
      val charWidth = boundingBox.width().toFloat() / word.length
      val deltaInChars: Float = if (charWidth > 0) delta.toFloat() / charWidth else 0f

      // In the same line but too far apart, make a new block
      if (deltaInChars >= 3f) { // TODO: how to figure out the delta better
        Log.d("OCRService", "Forcing new block with word $word")
        if (line.text.trim() != "") {
          lines.add(line)
        }
        line = TextLine(word, boundingBox, arrayOf(boundingBox))
        if (lines.isNotEmpty()) {
          blocks.add(TextBlock(lines.toTypedArray()))
          lines.clear()
        }
      } else {
        line.text = "${line.text} $word"
        line.wordRects += boundingBox
        if (boundingBox.right < line.boundingBox.left) {
          Log.e("OCRService", "going to break $boundingBox ${line.boundingBox}")
        }
        line.boundingBox.union(boundingBox)
        if (line.boundingBox.width() < 0) {
          Log.e("OCRService", "Found a line with bbw < 0: ${line.boundingBox}")
        }
      }
    }

    if (lastWordInLine && line.text.trim() != "") {
      lines.add(line)
      line = TextLine("", Rect(0, 0, 0, 0), emptyArray())
    }

    if (lastWordInPara && lines.isNotEmpty()) {
      blocks.add(TextBlock(lines.toTypedArray()))
      lines.clear()
    }
    lastRight = boundingBox.right
  }

  return blocks.toTypedArray()
}

class OCRService(
  private val filePathManager: FilePathManager,
) {
  private var tess: TessBaseAPI? = null
  private var isInitialized = false

  private suspend fun initialize(): Boolean =
    withContext(Dispatchers.IO) {
      if (isInitialized) return@withContext true

      try {
        val p = filePathManager.getTesseractDir().toPath()
        val tessdata = Path(p.pathString, "tessdata")
        val dataPath: String = p.absolutePathString()
        tessdata.createDirectories()

        // Get available language data
        val availableLanguages = getAvailableTessLanguages(tessdata.toFile()).map { it.tessName }
        if (availableLanguages.isEmpty()) {
          Log.w("OCRService", "No tessdata language files found")
          return@withContext false
        }

        tess = TessBaseAPI()

        val langs = availableLanguages.joinToString("+")
        Log.i("OCRService", "Initializing tesseract to path $dataPath, languages $langs")
        val initialized = tess?.init(dataPath, langs) ?: false
        if (!initialized) {
          tess?.recycle()
          tess = null
          // TODO popup
          Log.e(
            "OCRService",
            "Failed to initialize Tesseract with languages: $availableLanguages",
          )
          return@withContext false
        }

        isInitialized = true
        Log.i(
          "OCRService",
          "Tesseract initialized successfully with languages: $availableLanguages",
        )
        true
      } catch (e: Exception) {
        Log.e("OCRService", "Error initializing Tesseract", e)
        false
      }
    }

  suspend fun extractText(
    bitmap: Bitmap,
    minConfidence: Int = 75,
  ): Array<TextBlock> =
    withContext(Dispatchers.IO) {
      if (!isInitialized) {
        val initSuccess = initialize()
        if (!initSuccess) return@withContext emptyArray()
      }

      val tessInstance = tess ?: return@withContext emptyArray()

      val blocks: Array<TextBlock>
      val elapsed =
        measureTimeMillis {
          blocks = getSentences(bitmap, tessInstance, minConfidence)
        }
      // Release image data & results; but keeps the instance active
      Log.i("OCRService", "OCR took ${elapsed}ms")
      blocks
    }

  fun cleanup() {
    tess?.recycle()
    tess = null
    isInitialized = false
    Log.i("OCRService", "OCR service cleaned up")
  }
}
