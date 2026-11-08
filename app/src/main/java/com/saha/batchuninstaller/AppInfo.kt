/*
 * This file is part of Batch Uninstaller.
 *
 * Batch Uninstaller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Batch Uninstaller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Batch Uninstaller.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package com.saha.batchuninstaller

/**
 * Plain, immutable description of one installed app. Deliberately holds no Context or Bitmap so it
 * is cheap to build in bulk, safe to share across threads and easy to unit test. Icons are loaded
 * lazily by [com.saha.batchuninstaller.utils.IconLoader].
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    /** Size of the APK file(s) in bytes. */
    val size: Long,
    /** First install time, epoch millis. */
    val installTime: Long,
    val isSystem: Boolean
)
