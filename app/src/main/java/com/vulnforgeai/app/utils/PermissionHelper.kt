package com.vulnforgeai.app.utils

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Ajuda a pedir permissões de forma simples.
 */
object PermissionHelper {

    /** Diz se todas as permissões dadas já foram aceitas. */
    fun hasPermissions(activity: Activity, vararg permissions: String): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Pede as permissões que ainda faltam. */
    fun request(activity: Activity, requestCode: Int, vararg permissions: String) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        }
    }
}