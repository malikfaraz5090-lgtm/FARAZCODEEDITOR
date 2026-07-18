package com.example.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.runBlocking

class ProjectProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val path = uri.path ?: return null
        
        var project: Project? = null
        
        if (path == "/active") {
            project = activeProjectCache
        } else if (path.startsWith("/project/")) {
            val idStr = path.substringAfter("/project/")
            val id = idStr.toIntOrNull()
            if (id != null) {
                val context = context
                if (context != null) {
                    try {
                        runBlocking {
                            val db = ProjectDatabase.getDatabase(context)
                            project = db.projectDao().getProjectById(id)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val cursor = MatrixCursor(arrayOf(
            "html", "css", "js", "name", 
            "version", "company", "orientation", 
            "iconShape", "minSdkVersion", "enableSplashScreen", 
            "splashColor", "permissionInternet", "permissionCamera", 
            "permissionStorage", "customIconBase64", "iconColor", "iconSymbol"
        ))

        if (project != null) {
            val proj = project!!
            cursor.addRow(arrayOf<Any?>(
                proj.htmlCode, 
                proj.cssCode, 
                proj.jsCode, 
                proj.name,
                proj.appVersion,
                proj.companyName,
                proj.orientation,
                proj.iconShape,
                proj.minSdkVersion,
                if (proj.enableSplashScreen) 1 else 0,
                proj.splashColor,
                if (proj.permissionInternet) 1 else 0,
                if (proj.permissionCamera) 1 else 0,
                if (proj.permissionStorage) 1 else 0,
                proj.customIconBase64 ?: "",
                proj.iconColor,
                proj.iconSymbol
            ))
        } else {
            cursor.addRow(arrayOf<Any?>(
                "", "", "", "No Active Project",
                "1.0.0", "FC Studio", "Portrait",
                "Squircle", "Android 8.0 (API 26)", 1,
                0xFF1E1E1E.toInt(), 1, 0, 0, "", 0, ""
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        var activeProjectCache: Project? = null
    }
}
