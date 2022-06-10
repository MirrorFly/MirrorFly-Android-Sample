package com.contusfly.views

import android.animation.Animator
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.contusfly.R
import com.contusfly.chat.AndroidUtils
import com.contusfly.gone
import com.contusfly.interfaces.ChatAttachmentLister
import com.contusfly.show

open class ChatAttachmentDialog(
    context: Context,
    val attachment: View,
    private val footerDivider: View,
    private val footerBottom: View,
    private val transparentView: View,
    val chatAttachmentLister: ChatAttachmentLister?
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private lateinit var dialogView: View

    private var isKeyboardOpened = false
    private var screenHeight = 0

    lateinit var documentAttachment: TextView
    lateinit var cameraAttachment: TextView
    lateinit var galleryAttachment: TextView
    lateinit var audioAttachment: TextView
    lateinit var contactAttachment: TextView
    lateinit var locationAttachment: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.chatview_attachment_controls)

        dialogView = findViewById(R.id.layout_attachment)
        dialogView.visibility = View.INVISIBLE

        setDialogBackground()

        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        setOnKeyListener(object : DialogInterface.OnKeyListener {
            override fun onKey(dialog: DialogInterface?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
                    return true
                }
                return false
            }
        })

        setOnShowListener {
            dialogView.post {
                circularRevealDialog(true, dialogView, this@ChatAttachmentDialog)
            }
        }

        documentAttachment = dialogView.findViewById(R.id.document_attachment)
        cameraAttachment = dialogView.findViewById(R.id.camera_attachment)
        galleryAttachment = dialogView.findViewById(R.id.gallery_attachment)
        audioAttachment = dialogView.findViewById(R.id.audio_attachment)
        contactAttachment = dialogView.findViewById(R.id.contact_attachment)
        locationAttachment = dialogView.findViewById(R.id.location_attachment)

        documentAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachDocument()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
        cameraAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachCamera()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
        galleryAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachGallery()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
        audioAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachAudio()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
        contactAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachContact()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
        locationAttachment.setOnClickListener {
            chatAttachmentLister?.onAttachLocation()
            circularRevealDialog(false, dialogView, this@ChatAttachmentDialog)
        }
    }

    private fun setDialogBackground() {
        if (::dialogView.isInitialized) {
            if (isKeyboardOpened) {
                dialogView.background =
                    ContextCompat.getDrawable(context, R.drawable.attachement_keyboard_background)
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                lp.setMargins(0, 0, 0, 0)
                dialogView.layoutParams = lp
            } else {
                dialogView.background =
                    ContextCompat.getDrawable(context, R.drawable.attachment_background)
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(context.resources.getDimensionPixelSize(R.dimen.margin_12), 0, context.resources.getDimensionPixelSize(R.dimen.margin_12), 0)
                dialogView.layoutParams = lp
            }
        }

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            circularRevealDialog(false, dialogView, this)
            return true
        }
        return false
    }

    private fun circularRevealDialog(isExpand: Boolean, view: View, dialog: Dialog) {
        if (isExpand) transparentView.show()
        val cx = (attachment.left + attachment.right) / 2
        val cy = if (isKeyboardOpened) (attachment.top + attachment.bottom) / 2 else view.height

        val finalRadius = view.width
        val anim: Animator = ViewAnimationUtils.createCircularReveal(
            view, cx, cy, if (isExpand) 0f else finalRadius.toFloat(),
            if (isExpand) finalRadius.toFloat() else 0f
        )
        anim.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) { /*No Implementation Needed*/
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (!isExpand) {
                    dialog.dismiss()
                    view.visibility = View.INVISIBLE
                    transparentView.gone()
                }
            }

            override fun onAnimationCancel(animation: Animator?) { /*No Implementation Needed*/
            }

            override fun onAnimationRepeat(animation: Animator?) { /*No Implementation Needed*/
            }

        })
        anim.duration = 300
        if (isExpand)
            view.visibility = View.VISIBLE
        anim.start()
    }

    fun showDialog(isKeyboardOpened: Boolean, screenHeight: Int) {
        this.isKeyboardOpened = isKeyboardOpened
        this.screenHeight = screenHeight
        updateWindowView()
        show()
    }

    private fun updateWindowView() {
        if (isKeyboardOpened) {
            val lp: WindowManager.LayoutParams = window!!.attributes
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            val rectf = Rect()
            footerBottom.getGlobalVisibleRect(rectf)
            lp.height = screenHeight  - rectf.centerY()
            lp.gravity = Gravity.BOTTOM
            lp.y = 0
            lp.dimAmount = 0f
        } else {
            val lp: WindowManager.LayoutParams = window!!.attributes
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.BOTTOM
            val rectf = Rect()
            footerDivider.getGlobalVisibleRect(rectf)
            val y = rectf.centerY()
            lp.y = screenHeight - y + AndroidUtils.dp(5f, context)
            lp.dimAmount = 0f
        }
        setDialogBackground()
    }
}