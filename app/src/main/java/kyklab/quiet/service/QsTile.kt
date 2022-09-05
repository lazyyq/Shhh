package kyklab.quiet.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import kyklab.quiet.R
import kyklab.quiet.utils.openAppSettings
import kyklab.quiet.utils.PermissionManager


@RequiresApi(api = Build.VERSION_CODES.N)
class QsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        if (VolumeWatcherService.isRunning(this)) {
            setTileActive()
        } else {
            setTileInactive()
        }
    }

    override fun onClick() {
        super.onClick()
        if (PermissionManager.checkPermission(this)) {
            if (!VolumeWatcherService.isRunning(this)) {
                VolumeWatcherService.startService(this)
                setTileActive()
            } else {
                VolumeWatcherService.stopService(this)
                setTileInactive()
            }
        } else {
            Toast.makeText(this, R.string.msg_necessary_permission_missing, Toast.LENGTH_SHORT).show()
            openAppSettings() // TODO: FIX
        }
    }

    private fun setTileActive() {
        qsTile.apply {
            icon = Icon.createWithResource(this@QsTile, R.drawable.ic_speaker_mute)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun setTileInactive() {
        qsTile.apply {
            icon = Icon.createWithResource(this@QsTile, R.drawable.ic_speaker)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun setTileUnavailable() {
        qsTile.apply {
            icon = Icon.createWithResource(this@QsTile, R.drawable.ic_speaker_mute)
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }
}