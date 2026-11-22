package app.coverctl

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.concurrent.thread

/** Quick Settings: chạm để xoay Main → Dual → Cover → Main. */
class ScreensTile : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            val next = when (ScreenModes.Mode.fromId(Prefs.screenMode(this))) {
                ScreenModes.Mode.MAIN -> ScreenModes.Mode.DUAL
                ScreenModes.Mode.DUAL -> ScreenModes.Mode.COVER
                ScreenModes.Mode.COVER -> ScreenModes.Mode.MAIN
            }
            ScreenModes.apply(this, next, keepDual = Prefs.keepDual(this))
            qsTile?.let {
                updateTileLabel(it, next)
                it.updateTile()
            }
        }
    }

    private fun refresh() {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            val mode = try {
                ScreenModes.currentMode()
            } catch (_: Exception) {
                ScreenModes.Mode.fromId(Prefs.screenMode(this))
            }
            Prefs.setScreenMode(this, mode.id)
            qsTile?.let {
                updateTileLabel(it, mode)
                it.updateTile()
            }
        }
    }

    private fun updateTileLabel(tile: Tile, mode: ScreenModes.Mode) {
        tile.label = when (mode) {
            ScreenModes.Mode.MAIN -> getString(R.string.mode_main)
            ScreenModes.Mode.COVER -> getString(R.string.mode_cover)
            ScreenModes.Mode.DUAL -> getString(R.string.mode_dual)
        }
        tile.state = if (mode == ScreenModes.Mode.MAIN) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
    }
}
