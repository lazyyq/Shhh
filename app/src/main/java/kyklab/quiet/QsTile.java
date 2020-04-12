package kyklab.quiet;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import static kyklab.quiet.Utils.isServiceRunning;

public class QsTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();

        if (isServiceRunning(this, VolumeWatcherService.class)) {
            setTileActive();
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!isServiceRunning(this, VolumeWatcherService.class)) {
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
        Intent intent = new Intent(this, VolumeWatcherService.class);
        intent.setAction(Const.Intent.ACTION_START_SERVICE);
        startForegroundService(intent);
    }

    private void stopWatcherService() {
        Intent intent = new Intent(this, VolumeWatcherService.class);
        intent.setAction(Const.Intent.ACTION_STOP_SERVICE);
        startForegroundService(intent);
    }
}
