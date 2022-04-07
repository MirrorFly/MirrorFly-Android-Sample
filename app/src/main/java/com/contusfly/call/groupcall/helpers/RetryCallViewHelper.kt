package com.contusfly.call.groupcall.helpers

import android.view.View
import com.contus.webrtc.CallType
import com.contus.call.utils.GroupCallUtils
import com.contusfly.R
import com.contusfly.call.groupcall.listeners.ActivityOnClickListener
import com.contusfly.databinding.LayoutCallRetryBinding
import com.contusfly.gone
import com.contusfly.show

class RetryCallViewHelper(
    private val binding: LayoutCallRetryBinding,
    private val activityOnClickListener: ActivityOnClickListener
) : View.OnClickListener {
    init {
        binding.textCancel.setOnClickListener(this)
        binding.textCallAgain.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.text_cancel -> activityOnClickListener.cancelCallAgain()
            R.id.text_call_again -> activityOnClickListener.makeCallAgain()
        }
    }

    fun setUpCallUI() {
        hideRetryLayout()
    }

    fun showRetryLayout() {
        binding.layoutCallRetry.show()

        if (GroupCallUtils.isOneToOneCall()) {
            binding.textCallRetry.gone()
        } else {
            binding.textCallRetry.show()
        }

        binding.textCallAgain.setCompoundDrawablesWithIntrinsicBounds(
            0,
            if (GroupCallUtils.getCallType() == CallType.AUDIO_CALL) R.drawable.ic_group_call_again else R.drawable.ic_group_video_call_again,
            0,
            0
        )
    }

    fun hideRetryLayout() {
        binding.layoutCallRetry.gone()
    }
}