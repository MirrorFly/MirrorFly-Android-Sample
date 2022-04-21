package com.contusfly.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.contus.flycommons.LogMessage
import com.contusfly.R
import com.contusfly.views.DoProgressDialog
import com.contusflysdk.api.ChatManager.fileProviderAuthority
import com.contusflysdk.utils.FilePathUtils
import com.contusflysdk.media.MediaUploadHelper
import java.io.File

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object MediaUtils {

    /**
     * Load image with [Drawable] as a placeholder.
     *
     * @param context  Instance of the context
     * @param imageUrl   image url
     * @param imageView  Image view to display the image
     * @param defaultImage Display the drawable, if url return null
     */
    fun loadImage(context: Context, imageUrl: String?, imageView: ImageView, defaultImage: Drawable?) {
        var options = RequestOptions().placeholder(imageView.drawable ?: defaultImage).error(defaultImage).priority(Priority.HIGH)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            if (imageView.drawable != null) {
                options = RequestOptions().placeholder(imageView.drawable).error(imageView.drawable).priority(Priority.HIGH)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
            }
            val imgURL = Uri.parse(MediaUploadHelper.UPLOAD_ENDPOINT).buildUpon()
                    .appendPath(Uri.parse(imageUrl).lastPathSegment).build().toString()
            Glide.with(context).load(imgURL).thumbnail(0.1f).apply(options)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?,
                                                  isFirstResource: Boolean): Boolean {
                            return if (e?.message != null && e.message!!.contains("FileNotFoundException")) {
                                LogMessage.e("MediaUtils", "Token refresh error")
                                true
                            } else
                                false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?,
                                                     dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            return false
                        }
                    }).dontAnimate().dontTransform().into(imageView)
        } else
            Glide.with(context).load(defaultImage).apply(options).into(imageView)
    }

    /**
     * Load local image with glide with [Drawable] as a placeholder.
     *
     * @param context  Instance of the context
     * @param imgUrl   image url
     * @param imgView  Image view to display the image
     * @param errorImg Display the drawable, if url return null
     */
    @JvmStatic
    fun loadImageWithGlide(context: Context, imgUrl: String?, imgView: ImageView, errorImg: Drawable?) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val options = RequestOptions().placeholder(imgView.drawable ?: errorImg)
                    .error(errorImg).diskCacheStrategy(DiskCacheStrategy.ALL).priority(Priority.HIGH)
            Glide.with(context).load(imgUrl).thumbnail(0.1f).apply(options).into(imgView)
        } else imgView.setImageDrawable(errorImg)
    }

    /**
     * Load image with glide.
     *
     * @param context  Instance of the context
     * @param imgFile  Local path
     * @param imgView  Image view to display the image
     * @param errorImg Display the message is url return null
     */
    @JvmStatic
    fun loadImageWithGlide(context: Context?, imgFile: File?, imgView: ImageView?, errorImg: Int) {
        val options = RequestOptions()
                .placeholder(errorImg)
                .error(errorImg)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
        Glide.with(context!!).load(imgFile).thumbnail(0.1f).apply(options).into(imgView!!)
    }

    /**
     * Load image with glide.
     *
     * @param context  Instance of the context
     * @param imgUrl   Image uri
     * @param imgView  Image view to display the image
     * @param errorImg Display the message is url return null
     */
    @JvmStatic
    fun loadImageWithGlide(context: Context?, imgUrl: String?, imgView: ImageView, errorImg: Int) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val options = RequestOptions()
                    .placeholder(errorImg)
                    .error(errorImg)
                    .dontAnimate()
                    .dontTransform()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .priority(Priority.HIGH)
            Glide.with(context!!).load(imgUrl).thumbnail(0.1f).apply(options).into(imgView)
        } else imgView.setImageResource(errorImg)
    }

    /**
     * Load image with glide.
     *
     * @param context  Instance of the context
     * @param imgUrl   Image uri
     * @param imgView  Image view to display the image
     * @param errorImg Display the message is url return null
     */
    @JvmStatic
    fun loadImageWithGlideAnimate(context: Context?, imgUrl: String?, imgView: ImageView, errorImg: Int) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val options = RequestOptions().frame(1000)
                .placeholder(errorImg)
                .error(errorImg)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
            Glide.with(context!!).load(imgUrl).thumbnail(0.1f).apply(options).into(imgView)
        } else imgView.setImageResource(errorImg)
    }

    /**
     * Open media file using intent action view
     *
     * @param context  Application context
     * @param filePath The file path
     */
    @JvmStatic
    fun openMediaFile(context: Context, filePath: String?) {
        val file = File(filePath)
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val fileUri = FileProvider.getUriForFile(context, fileProviderAuthority, file)
        intent.setDataAndType(fileUri, mimeType)
        val mediaListIntent = Intent(Intent.ACTION_VIEW, fileUri)
        mediaListIntent.type = mimeType
        val mediaViewerApps: List<ResolveInfo> = context.packageManager.queryIntentActivities(mediaListIntent, 0)
        try {
            when {
                intent.resolveActivity(context.packageManager) != null -> context.startActivity(intent)
                mediaViewerApps.isNotEmpty() -> context.startActivity(intent)
                else -> Toast.makeText(context, R.string.file_viewing_message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.file_viewing_message, Toast.LENGTH_LONG).show()
        }
    }

    fun loadImageWithLoader(context: Context, imageUrl: String?, imageView: ImageView, defaultImage: Drawable?, progressDialog: DoProgressDialog?) {
        var options = RequestOptions().placeholder(defaultImage).error(defaultImage).priority(Priority.HIGH)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            if (imageView.drawable != null) {
                options = RequestOptions().placeholder(imageView.drawable).error(imageView.drawable).priority(Priority.HIGH)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
            }
            val imgURL = Uri.parse(MediaUploadHelper.UPLOAD_ENDPOINT).buildUpon()
                    .appendPath(Uri.parse(imageUrl).lastPathSegment).build().toString()
            Glide.with(context).load(imgURL).thumbnail(0.1f).apply(options)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?,
                                                  isFirstResource: Boolean): Boolean {
                            return if (e?.message != null && e.message!!.contains("FileNotFoundException")) {
                                LogMessage.e("MediaUtils", "Token refresh error")
                                if (progressDialog!= null && progressDialog.isShowing) progressDialog.dismiss()
                                true
                            } else {
                                progressDialog?.dismiss()
                                false
                            }
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?,
                                                     dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            if (progressDialog!= null && progressDialog.isShowing) progressDialog.dismiss()
                            return false
                        }
                    }).dontAnimate().dontTransform().into(imageView)
        } else {
            Glide.with(context).load(defaultImage).apply(options).into(imageView)
            if(progressDialog!= null && progressDialog.isShowing) progressDialog.dismiss()
        }
    }

    /**
     * Load image with glide with [Drawable] as a placeholder.
     *
     * @param context  Instance of the context
     * @param imgUrl   image url
     * @param imgView  Image view to display the image
     * @param errorImg Display the drawable, if url return null
     */
    fun loadImageWithGlideSecure(context: Context?, imgUrl: String?,
                                 imgView: ImageView, errorImg: Drawable?) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val imageUrl = Uri.parse(MediaUploadHelper.UPLOAD_ENDPOINT)
                    .buildUpon().appendPath(Uri.parse(imgUrl).lastPathSegment).build().toString()
            val options = RequestOptions().placeholder(errorImg).error(errorImg)
                    .priority(Priority.HIGH).diskCacheStrategy(DiskCacheStrategy.ALL)
            Glide.with(context!!).load(imageUrl).thumbnail(0.1f).apply(options)
                    .addListener(object : RequestListener<Drawable?> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>?, isFirstResource: Boolean): Boolean {
                            return if (e != null && e.message!!.contains("FileNotFoundException")) {
                                LogMessage.e("MediaUtils", "Token refresh error")
                                true
                            } else false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            return false
                        }
                    })
                    .into(imgView)
        } else imgView.setImageDrawable(errorImg)
    }

    /**
     * Returns sent local folder path from app folder
     *
     * @param folderName Folder name
     * @return String Sent folder path for media
     */
    fun getSentPath(folderName: String): String {
        return (FilePathUtils.getExternalStorage().toString() + File.separator
                + Constants.LOCAL_PATH + File.separator + folderName + File.separator
                + Constants.MSG_SENT_PATH)
    }

    /**
     * Load image with glide.
     *
     * @param context  Instance of the context
     * @param imgUrl   Image uri
     * @param imgView  Image view to display the image
     * @param errorImg Display the message is url return null
     */
    @JvmStatic
    fun loadImageWithGlideSkipCache(context: Context?, imgUrl: String?, imgView: ImageView, errorImg: Int) {
        if (imgUrl != null && imgUrl.isNotEmpty()) {
            val options = RequestOptions()
                .placeholder(errorImg)
                .error(errorImg)
                .dontAnimate()
                .dontTransform()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .priority(Priority.HIGH)
                .skipMemoryCache(true)
            Glide.with(context!!).load(imgUrl).thumbnail(0.1f).apply(options).into(imgView)
        } else imgView.setImageResource(errorImg)
    }

    fun loadImageWithGlide(
        context: Context?,
        frame: Bitmap?,
        imgView: ImageView,
        errorImg: Int
    ) {
        if (frame != null) {
            val options = RequestOptions()
                .placeholder(errorImg)
                .error(errorImg)
                .dontAnimate()
                .dontTransform()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
            Glide.with(context!!).load(frame).thumbnail(0.1f).apply(options).into(imgView)
        } else imgView.setImageResource(errorImg)
    }
}