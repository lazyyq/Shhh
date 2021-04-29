package kyklab.quiet;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QsTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();

        if (VolumeWatcherService.isRunning(this)) {
            setTileActive();
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!VolumeWatcherService.isRunning(this)) {
            startWatcherService();
            setTileActive();
        } else {
            stopWatcherService();
            setTileInactive();
        }
    }

    private void setTileActive() {
        Tile tile = getQsTile();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_speaker_mute));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    private void setTileInactive() {
        Tile tile = getQsTile();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_speaker));
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void startWatcherService() {
        VolumeWatcherService.startService(this);
    }

    private void stopWatcherService() {
        VolumeWatcherService.stopService(this);
    }
}
