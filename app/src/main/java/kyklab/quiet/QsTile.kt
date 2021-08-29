package kyklab.quiet

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.N)
class QsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        if (VolumeWatcherService.isRunning(this)) {
            setTileActive()
        }
    }

    override fun onClick() {
        super.onClick()
        if (!VolumeWatcherService.isRunning(this)) {
            VolumeWatcherService.startService(this)
            setTileActive()
        } else {
            VolumeWatcherService.stopService(this)
            setTileInactive()
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
}