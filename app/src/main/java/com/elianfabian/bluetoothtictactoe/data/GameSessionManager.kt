package com.elianfabian.bluetoothtictactoe.data

import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.model.BluetoothDevice
import com.elianfabian.lapisbt_rpc.LapisBtRpc
import com.elianfabian.bluetoothtictactoe.rpc.TicTacToeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GameSessionManager(
    private val lapisBt: LapisBt,
    private val lapisBtRpc: LapisBtRpc,
    private val serviceUuid: UUID
) {
    val state = MutableStateFlow(PlayerState.Free)
    val activeGameConfig = MutableStateFlow<GameConfig?>(null)
    val activeDataSource = MutableStateFlow<TicTacToeDataSource?>(null)

    fun clearActiveGame() {
        activeGameConfig.value = null
        activeDataSource.value = null
    }

    fun getOrCreateDataSource(address: BluetoothDevice.Address, isHost: Boolean): TicTacToeDataSource {
        return activeDataSource.value ?: if (isHost) {
            LocalTicTacToeDataSource().also { activeDataSource.value = it }
        } else {
            val proxy = lapisBtRpc.getOrCreateBluetoothClientService(address, TicTacToeService::class)
            RemoteTicTacToeDataSource(proxy).also { activeDataSource.value = it }
        }
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
                    lapisBt.startBluetoothServerWithoutPairing("TicTacToe", serviceUuid)
                } else {
                    lapisBt.stopBluetoothServer(serviceUuid)
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
                        lapisBt.startBluetoothServerWithoutPairing("TicTacToe", serviceUuid)
                    }
                }
            }
        }
    }
}
