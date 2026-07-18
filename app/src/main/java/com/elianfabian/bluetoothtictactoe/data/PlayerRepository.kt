package com.elianfabian.bluetoothtictactoe.data

import com.elianfabian.bluetoothtictactoe.LapisBtProvider.TIC_TAC_TOE_UUID
import com.elianfabian.lapisbt.LapisBt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PlayerRepository(
    private val lapisBt: LapisBt
) {
    val state = MutableStateFlow(PlayerState.Free)
    val activeGameConfig = MutableStateFlow<GameConfig?>(null)

    fun clearActiveGame() {
        activeGameConfig.value = null
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        startBluetoothServer()
        observeBluetoothEvents()
    }

    private fun startBluetoothServer() {
        scope.launch {
            lapisBt.state.collect { btState ->
                if (btState.isOn) {
                    lapisBt.startBluetoothServerWithoutPairing("TicTacToe", TIC_TAC_TOE_UUID)
                } else {
                    lapisBt.stopBluetoothServer(TIC_TAC_TOE_UUID)
                }
            }
        }
    }

    private fun observeBluetoothEvents() {
        scope.launch {
            lapisBt.events.collect { event ->
                if (event is LapisBt.Event.OnDeviceConnected) {
                    // Restart server after a connection to stay discoverable
                    if (lapisBt.state.value.isOn) {
                        lapisBt.startBluetoothServerWithoutPairing("TicTacToe", TIC_TAC_TOE_UUID)
                    }
                }
            }
        }
    }
}
