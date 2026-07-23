/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.mavodev.openvpnneo.R

class RemoteCNPreference : DialogPreference {
    var authtype: Int = 0
        private set
    var cnText: String? = null
        private set

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    fun setDN(dn: String?) {
        cnText = dn
        notifyChanged()
    }

    fun setAuthType(x509authtype: Int) {
        authtype = x509authtype
        notifyChanged()
    }

    override fun getDialogLayoutResource(): Int = R.layout.tlsremote
}
