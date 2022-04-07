package com.contusfly.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.contusfly.R
import com.google.android.material.snackbar.Snackbar

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object MediaPermissions {
    /**
     * Request the storage permission for accessing Gallery
     *
     * @param activity       Activity of the View
     * @param permissionsLauncher permission launcher to request Permission
     */
    fun requestStorageAccess(activity: Activity, permissionsLauncher: ActivityResultLauncher<Array<String>>) {

        val hasReadPermission = isPermissionAllowed(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
        val hasWritePermission = isPermissionAllowed(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val minSdk30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q

        val writePermissionGranted = hasWritePermission || minSdk30

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!hasReadPermission) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissionsToRequest.isNotEmpty()) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    Snackbar.make(activity.findViewById(android.R.id.content), R.string.storage_permission_label,
                        Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok_label) {
                        showPermissionPopUpForStorage(permissionsLauncher, permissionsToRequest)
                    }.show()
                }
                SharedPreferenceManager.getBoolean(Constants.STORAGE_PERMISSION_ASKED) -> {
                    openSettingsForPermission(activity, activity.getString(R.string.storage_permission_label))
                }
                else -> {
                    showPermissionPopUpForStorage(permissionsLauncher, permissionsToRequest)
                }
            }
        }
    }

    private fun showPermissionPopUpForStorage(permissionsLauncher: ActivityResultLauncher<Array<String>>, permissionsToRequest: MutableList<String>) {
        SharedPreferenceManager.setBoolean(Constants.STORAGE_PERMISSION_ASKED, true)
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    /**
     * Request the camera and audio permissions for making audio/video calls
     *
     * @param activity       Activity of the View
     * @param permissionCode Code for start activity
     */
    fun requestVideoCallPermissions(activity: Activity, permissionCode: Int) {
        if (!isPermissionAllowed(activity, Manifest.permission.CAMERA) ||
            !isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO) ||
            !isPermissionAllowed(activity, Manifest.permission.READ_PHONE_STATE)
        ) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.CAMERA
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_PHONE_STATE
                )
                -> {
                    /*
                  If the user has denied the permission previously your code will come to this block
                  Here you can explain why you need this permission Explain here why you need this
                  permission
                 */
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.video_record_permission_label,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.ok_label) {
                            askVideoCallPermissions(activity, permissionCode)
                        }
                        .show()
                }
                (SharedPreferenceManager.getBoolean(Constants.CAMERA_PERMISSION_ASKED) &&
                        !isPermissionAllowed(activity, Manifest.permission.CAMERA)) ||
                        (SharedPreferenceManager.getBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED) &&
                                !isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO)) ||
                        (SharedPreferenceManager.getBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED) &&
                                !isPermissionAllowed(
                                    activity,
                                    Manifest.permission.READ_PHONE_STATE
                                )) -> {
                    openSettingsForPermission(
                        activity,
                        activity.getString(R.string.video_record_permission_label)
                    )
                }
                else -> {
                    //And finally ask for the permission.
                    askVideoCallPermissions(activity, permissionCode)
                }
            }
        }
    }

    private fun askVideoCallPermissions(activity: Activity, permissionCode: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CAMERA
            ), permissionCode
        )

        SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED, true)
    }

    /**
     * Request the camera and audio permissions for making audio/video calls
     *
     * @param activity       Activity of the View
     * @param activityResultCaller activityResultCaller
     */
    fun requestVideoCallPermissions(
        activity: Activity,
        activityResultCaller: ActivityResultLauncher<Array<String>>
    ) {
        if (!isPermissionAllowed(activity, Manifest.permission.CAMERA) ||
            !isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO) ||
            !isPermissionAllowed(activity, Manifest.permission.READ_PHONE_STATE)
        ) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.CAMERA
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_PHONE_STATE
                )
                -> {
                    /*
                  If the user has denied the permission previously your code will come to this block
                  Here you can explain why you need this permission Explain here why you need this
                  permission
                 */
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.video_record_permission_label,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.ok_label) {
                            askVideoCallPermissions(activityResultCaller)
                        }
                        .show()
                }
                SharedPreferenceManager.getBoolean(Constants.CAMERA_PERMISSION_ASKED) ||
                        SharedPreferenceManager.getBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED) ||
                        SharedPreferenceManager.getBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED) -> {
                    openSettingsForPermission(
                        activity,
                        activity.getString(R.string.video_record_permission_label)
                    )
                }
                else -> {
                    //And finally ask for the permission.
                    askVideoCallPermissions(activityResultCaller)
                }
            }
        }
    }

    private fun askVideoCallPermissions(activityResultCaller: ActivityResultLauncher<Array<String>>) {
        activityResultCaller.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CAMERA
            )
        )
        SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED, true)
    }

    /**
     * Request the camera and audio permissions for making audio/video calls
     *
     * @param activity       Activity of the View
     * @param permissionCode Code for start activity
     */
    fun requestAudioCallPermissions(activity: Activity, permissionCode: Int) {
        if (!isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO) ||
            !isPermissionAllowed(activity, Manifest.permission.READ_PHONE_STATE)
        ) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_PHONE_STATE
                ) -> {
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.record_permission_label,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.ok_label) {
                            doRequestAudioCallPermissions(
                                activity,
                                permissionCode
                            )
                        }.show()
                }

                ((SharedPreferenceManager.getBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED) &&
                        isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO)) ||
                        (SharedPreferenceManager.getBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED) &&
                                isPermissionAllowed(activity, Manifest.permission.READ_PHONE_STATE))) -> {
                    openSettingsForPermission(
                        activity,
                        activity.getString(R.string.record_permission_label)
                    )
                }
                else -> doRequestAudioCallPermissions(activity, permissionCode)
            }
        }
    }

    /*clent side code fix*/
    /**
     * Perform the request permissions
     *
     * @param activity       the activity
     * @param fragment       the fragment
     * @param permissionCode the permisison code
     */
    private fun doRequestAudioCallPermissions(activity: Activity, permissionCode: Int) {
        val permissionsArray = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        // onRequestPermissionsResult will be called in Activity
        ActivityCompat.requestPermissions(activity, permissionsArray, permissionCode)

        SharedPreferenceManager.setBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED, true)
    }

    /**
     * Request the camera and audio permissions for making audio/video calls
     *
     * @param activity       Activity of the View
     * @param activityResultCaller activityResultCaller
     */
    fun requestAudioCallPermissions(
        activity: Activity, activityResultCaller: ActivityResultLauncher<Array<String>>,
    ) {
        if (!isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO) ||
            !isPermissionAllowed(activity, Manifest.permission.READ_PHONE_STATE)
        ) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.READ_PHONE_STATE
                ) -> {
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.record_permission_label,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.ok_label) {
                            doRequestAudioCallPermissions(activityResultCaller)
                        }.show()
                }

                SharedPreferenceManager.getBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED) ||
                        SharedPreferenceManager.getBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED) -> {
                    openSettingsForPermission(
                        activity,
                        activity.getString(R.string.record_permission_label)
                    )
                }
                else -> doRequestAudioCallPermissions(activityResultCaller)
            }
        }
    }

    /**
     * Perform the request permissions via activityResultCaller
     *
     * @param activityResultCaller  activityResultCaller
     */
    private fun doRequestAudioCallPermissions(activityResultCaller: ActivityResultLauncher<Array<String>>) {
        activityResultCaller.launch(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        ))
        SharedPreferenceManager.setBoolean(Constants.RECORD_AUDIO_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.READ_PHONE_STATE_PERMISSION_ASKED, true)
    }

    /**
     * Calling this method to check the permission status
     *
     * @param context    Context of the activity
     * @param permission Permission to ask
     * @return boolean True if grand the permission
     */
    fun isPermissionAllowed(context: Context?, permission: String?): Boolean {
        return ContextCompat.checkSelfPermission(context!!, permission!!) == PackageManager.PERMISSION_GRANTED
    }

    fun isReadFilePermissionAllowed(context: Context?): Boolean {
        return isPermissionAllowed(context!!, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun isWriteFilePermissionAllowed(context: Context?): Boolean {
        val minSdk30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
        return isPermissionAllowed(context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) || minSdk30
    }

    /**
     * Request the camera permission from the camera chosen for take photo
     *
     * @param activity       Activity of the View
     * @param permissionsLauncher permission launcher to request Permission
     */
    fun requestCameraStoragePermissions(activity: Activity, permissionsLauncher: ActivityResultLauncher<Array<String>>) {
        val hasReadPermission = isPermissionAllowed(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
        val hasCameraPermission = isPermissionAllowed(activity, Manifest.permission.CAMERA)
        val hasWritePermission = isPermissionAllowed(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val minSdk30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q

        val writePermissionGranted = hasWritePermission || minSdk30

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!hasReadPermission) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (!hasCameraPermission) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (permissionsToRequest.isNotEmpty()) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                        || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                -> {
                    /*
                     If the user has denied the permission previously your code will come to this block
                     Here you can explain why you need this permission Explain here why you need this
                     permission
                     */
                    Snackbar.make(activity.findViewById(android.R.id.content), R.string.storage_camera_permission_label,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.ok_label) {
                            showPermissionPopUpForCamera(permissionsLauncher, permissionsToRequest)
                        }
                        .show()
                }
                SharedPreferenceManager.getBoolean(Constants.CAMERA_PERMISSION_ASKED)
                        && SharedPreferenceManager.getBoolean(Constants.STORAGE_PERMISSION_ASKED)
                -> {
                    openSettingsForPermission(activity, activity.getString(R.string.storage_camera_permission_label))
                }
                else -> {
                    showPermissionPopUpForCamera(permissionsLauncher, permissionsToRequest)
                }
            }
        }
    }

    private fun showPermissionPopUpForCamera(permissionsLauncher: ActivityResultLauncher<Array<String>>, permissionsToRequest: MutableList<String>) {
        SharedPreferenceManager.setBoolean(Constants.STORAGE_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    /**
     * Request the camera permission from the camera chosen for take photo
     *
     * @param activity       Activity of the View
     * @param permissionsLauncher permission launcher to request Permission
     */
    fun requestAudioStoragePermissions(activity: Activity, permissionsLauncher: ActivityResultLauncher<Array<String>>) {
            val hasReadPermission = isPermissionAllowed(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
            val hasRecordAudioPermission = isPermissionAllowed(activity, Manifest.permission.RECORD_AUDIO)
            val hasWritePermission = isPermissionAllowed(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

            val minSdk30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q

            val writePermissionGranted = hasWritePermission || minSdk30

            val permissionsToRequest = mutableListOf<String>()
            if (!writePermissionGranted) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (!hasReadPermission) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (!hasRecordAudioPermission) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }
            if (permissionsToRequest.isNotEmpty()) {
                when {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
                            || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    -> {
                        /*
                         If the user has denied the permission previously your code will come to this block
                         Here you can explain why you need this permission Explain here why you need this
                         permission
                         */
                        Snackbar.make(activity.findViewById(android.R.id.content), R.string.audio_record_permission_label, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.ok_label) {
                                showPermissionPopUpForAudioRecord(permissionsLauncher, permissionsToRequest)
                            }
                            .show()
                    }
                    SharedPreferenceManager.getBoolean(Constants.AUDIO_RECORD_PERMISSION_ASKED)
                            && SharedPreferenceManager.getBoolean(Constants.STORAGE_PERMISSION_ASKED)
                    -> {
                        openSettingsForPermission(activity, activity.getString(R.string.audio_record_permission_label))
                    }
                    else -> {
                        showPermissionPopUpForAudioRecord(permissionsLauncher, permissionsToRequest)
                    }
                }
            }
    }

    private fun showPermissionPopUpForAudioRecord(permissionsLauncher: ActivityResultLauncher<Array<String>>, permissionsToRequest: MutableList<String>) {
        SharedPreferenceManager.setBoolean(Constants.AUDIO_RECORD_PERMISSION_ASKED, true)
        SharedPreferenceManager.setBoolean(Constants.STORAGE_PERMISSION_ASKED, true)
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    /**
     * Request the read contacts permission
     *
     * @param activity       Activity of the View
     * @param permissionsLauncher permission launcher to request Permission
     */
    fun requestContactsReadPermission(activity: Activity, permissionsLauncher: ActivityResultLauncher<Array<String>>) {
            val hasContactPermission = isPermissionAllowed(activity, Manifest.permission.READ_CONTACTS)

            val permissionsToRequest = mutableListOf<String>()
            if (!hasContactPermission) {
                permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
            }
            if (permissionsToRequest.isNotEmpty()) {
                when {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS) -> {
                        /*
                          If the user has denied the permission previously your code will come to this block
                          Here you can explain why you need this permission Explain here why you need this
                          permission
                        */
                        Snackbar.make(activity.findViewById(android.R.id.content), R.string.read_contact_permission_label,
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.ok_label) { showPermissionPopUpForContact(permissionsLauncher, permissionsToRequest) }
                            .show()
                    }
                    SharedPreferenceManager.getBoolean(Constants.CONTACT_PERMISSION_ASKED) -> {
                        openSettingsForPermission(activity, activity.getString(R.string.read_contact_permission_label))
                    }
                    else -> {
                        showPermissionPopUpForContact(permissionsLauncher, permissionsToRequest)
                    }
                }
            }
    }

    private fun showPermissionPopUpForContact(permissionsLauncher: ActivityResultLauncher<Array<String>>, permissionsToRequest: MutableList<String>) {
        SharedPreferenceManager.setBoolean(Constants.CONTACT_PERMISSION_ASKED, true)
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun openSettingsForPermission(activity: Activity, message: String) {
        Snackbar.make(activity.findViewById(android.R.id.content), message,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok_label) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.packageName, null))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    activity.startActivity(intent)
                }
                .show()
    }

    /**
     * Calling this method to check the permission status of the location
     *
     * @param context Context of the activity
     * @return boolean True if grand the permission
     */
    fun isLocationPermissionAllowed(context: Context?): Boolean {
        /*
        * Getting the permission status
        */
        val fineLocation = ContextCompat.checkSelfPermission(context!!, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        /*
         *If permission is granted returning true
         */
        return fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED
    }


    /**
     * Requesting permission for the specific permission from the user
     *
     * @param activity       Activity of the View
     * @param permissionsLauncher permission launcher to request Permission
     */
    fun requestLocationPermission(activity: Activity, permissionsLauncher: ActivityResultLauncher<Array<String>>) {
            val hasLocationPermission = isLocationPermissionAllowed(activity)

            val permissionsToRequest = mutableListOf<String>()
            if (!hasLocationPermission) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (permissionsToRequest.isNotEmpty()) {
                when {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                        /*
                          If the user has denied the permission previously your code will come to this block
                          Here you can explain why you need this permission Explain here why you need this
                          permission
                        */
                        Snackbar.make(activity.findViewById(android.R.id.content), R.string.message_permission_location_rationale,
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.ok_label) { showPermissionPopUpForLocation(permissionsLauncher, permissionsToRequest) }
                            .show()
                    }
                    SharedPreferenceManager.getBoolean(Constants.LOCATION_PERMISSION_ASKED) -> {
                        openSettingsForPermission(activity, activity.getString(R.string.message_permission_location_rationale))
                    }
                    else -> {
                        showPermissionPopUpForLocation(permissionsLauncher, permissionsToRequest)
                    }
                }
            }
    }

    private fun showPermissionPopUpForLocation(permissionsLauncher: ActivityResultLauncher<Array<String>>, permissionsToRequest: MutableList<String>) {
        SharedPreferenceManager.setBoolean(Constants.LOCATION_PERMISSION_ASKED, true)
        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    /**
     * Request the telephony call permissions
     *
     * @param activity       Activity of the View
     * @param permissionCode Code for start activity
     */
    fun requestTelephoneCallPermissions(activity: Activity, permissionCode: Int) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CALL_PHONE)) {
            /*
              If the user has denied the permission previously your code will come to this block
              Here you can explain why you need this permission Explain here why you need this
              permission
             */
            Snackbar.make(activity.findViewById(android.R.id.content), R.string.calling_permission_label,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok_label) { ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CALL_PHONE), permissionCode) }
                    .show()
        } else {
            /*
          And finally ask for the permission.
         */
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CALL_PHONE), permissionCode)
        }
    }

    /**
     * check whether permission should show or not
     *
     * @param activity   the current activity instance
     * @param permission the required permission
     * @return <tt>true</tt> if permission should show; otherwise false
     */
    fun canRequestPermission(activity: Activity, permission: String?): Boolean {
        return SDK_INT >= Build.VERSION_CODES.M && activity.shouldShowRequestPermissionRationale(
            permission!!
        )
    }

    /**
     * Request the read contacts permission
     *
     * @param activity       Activity of the View
     * @param permissionCode Code for start activity
     */
    fun requestCameraPermission(activity: Activity, permissionCode: Int) {
        if (!isPermissionAllowed(activity, Manifest.permission.CAMERA)) {
            when {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                ) -> {
                    /*
                      If the user has denied the permission previously your code will come to this block
                      Here you can explain why you need this permission Explain here why you need this
                      permission
                     */
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        R.string.permission_camera_rationale,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.ok) {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.CAMERA),
                            permissionCode
                        )
                        SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
                    }
                        .show()
                }
                SharedPreferenceManager.getBoolean(Constants.CAMERA_PERMISSION_ASKED) -> {
                    openSettingsForPermission(
                        activity,
                        activity.getString(R.string.camera_permission_label)
                    )
                }
                else -> {
                    // ask for the permission
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.CAMERA),
                        permissionCode
                    )
                    SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
                }
            }
        }
    }

    /**
     * Request the read/write storage permissions
     *
     * @param activity       Activity of the View
     * @param permissionCode Code for start activity
     */
    fun requestStoragePermissions(activity: Activity, permissionCode: Int) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            /*
              If the user has denied the permission previously your code will come to this block
              Here you can explain why you need this permission Explain here why you need this
              permission
             */
            Snackbar.make(
                activity.findViewById(android.R.id.content), R.string.storage_permission_label,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.ok) { view ->
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ), permissionCode
                    )
                }
                .show()
        } else {
            /*
          And finally ask for the permission.
         */
            ActivityCompat.requestPermissions(
                activity, arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), permissionCode
            )
        }
    }
}