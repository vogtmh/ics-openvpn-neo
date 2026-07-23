/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.mavodev.openvpnneo.R
import java.util.concurrent.Executors

class FaqViewAdapter(
    private val mContext: Context,
    private val mFaqItems: Array<FaqFragment.FAQEntry>
) : RecyclerView.Adapter<FaqViewAdapter.FaqViewHolder>() {

    private val mHtmlEntries = arrayOfNulls<Spanned>(mFaqItems.size)
    private val mHtmlEntriesTitle = arrayOfNulls<Spanned>(mFaqItems.size)
    private var loaded = false

    init {
        Executors.newSingleThreadExecutor().execute {
            fetchStrings(mFaqItems)
            Handler(Looper.getMainLooper()).post {
                loaded = true
                @SuppressLint("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }
    }

    private fun fetchStrings(faqItems: Array<FaqFragment.FAQEntry>) {
        for (i in faqItems.indices) {
            val versionText = mFaqItems[i].getVersionsString(mContext)
            val title = if (mFaqItems[i].title == -1) "" else mContext.getString(faqItems[i].title)
            var textColor = ""

            if (!mFaqItems[i].runningVersion()) {
                textColor = "<font color=\"gray\">"
            }

            mHtmlEntriesTitle[i] = if (versionText != null) {
                TextUtils.concat(
                    HtmlCompat.fromHtml(textColor + title, HtmlCompat.FROM_HTML_MODE_LEGACY),
                    HtmlCompat.fromHtml(
                        textColor + "<br><small>" + versionText + "</small>",
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                ) as Spanned
            } else {
                HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
            }

            val content = mContext.getString(faqItems[i].description)
            mHtmlEntries[i] = HtmlCompat.fromHtml(textColor + content, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    class FaqViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mView: CardView = itemView as CardView
        val mBody: TextView = mView.findViewById(R.id.faq_body)
        val mHead: TextView = mView.findViewById(R.id.faq_head)

        init {
            mBody.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): FaqViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.faqcard, viewGroup, false)
        return FaqViewHolder(view)
    }

    override fun onBindViewHolder(faqViewHolder: FaqViewHolder, i: Int) {
        faqViewHolder.mHead.text = mHtmlEntriesTitle[i]
        faqViewHolder.mBody.text = mHtmlEntries[i]
    }

    override fun getItemCount(): Int = if (loaded) mFaqItems.size else 0
}
