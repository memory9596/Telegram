package org.telegram.ui.Components.chat

import android.graphics.Canvas
import android.text.Spanned
import android.text.StaticLayout
import android.text.style.ReplacementSpan
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessageObject
import org.telegram.ui.CachedStaticLayout
import org.telegram.ui.Components.AnimatedEmojiSpan
import java.util.IdentityHashMap

/** Owns only the stable text display lists; animated emoji are drawn by the cell afterwards. */
class CaptionTextDrawingCache {
    private var layouts: List<MessageObject.TextLayoutBlock>? = null
    private val blocks = IdentityHashMap<StaticLayout, CachedStaticLayout>()

    fun setCaption(caption: MessageObject.TextLayoutBlocks?) {
        if (layouts !== caption?.textLayoutBlocks) {
            clear()
            layouts = caption?.textLayoutBlocks
        }
    }

    fun draw(canvas: Canvas, caption: MessageObject.TextLayoutBlocks, block: MessageObject.TextLayoutBlock) {
        setCaption(caption)
        val cached = blocks.getOrPut(block.textLayout) {
            CachedStaticLayout(block.textLayout).cacheEmojiPositions().also {
                val text = block.textLayout.text
                if (text is Spanned && text.getSpans(0, text.length, ReplacementSpan::class.java)
                        .any { span -> span !is AnimatedEmojiSpan && span !is Emoji.EmojiSpan }) {
                    it.disableCache()
                }
            }
        }
        try {
            cached.draw(canvas)
        } catch (e: RuntimeException) {
            cached.clear()
            cached.disableCache()
            FileLog.e(e)
            block.textLayout.draw(canvas)
        }
    }

    fun clear() {
        blocks.values.forEach { it.clear() }
        blocks.clear()
        layouts = null
    }
}
