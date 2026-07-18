package com.elianfabian.bluetoothtictactoe.ui.discovery

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.activity_result_bridge.ActivityResultBridge
import com.elianfabian.bluetoothtictactoe.LapisBtProvider
import com.elianfabian.bluetoothtictactoe.LapisBtProvider.TIC_TAC_TOE_UUID
import com.elianfabian.bluetoothtictactoe.data.GameConfig
import com.elianfabian.bluetoothtictactoe.data.InvitationResponse
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.rpc.InvitationService
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.model.BluetoothDevice
import com.elianfabian.lapisbt.model.ScannedBluetoothDevice
import com.elianfabian.lapisbt_rpc.getLapisRequestInfo
import com.elianfabian.yuru_permissions.Yuru
import com.elianfabian.yuru_permissions.YuruPermissionState
import com.zhuinden.flowcombinetuplekt.combineTuple
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceDiscoveryViewModel(
	context: Context,
	private val yuru: Yuru = Yuru.getInstance(),
	private val activityResultBridge: ActivityResultBridge = ActivityResultBridge.getInstance(),
) : ViewModel(), InvitationService {

	private val lapisBt = LapisBtProvider.getLapisBt(context)
	private val lapisBtRpc = LapisBtProvider.getLapisBtRpc(context)
	private val playerRepository = LapisBtProvider.getPlayerRepository(context)

	private val remotePlayerStates = MutableStateFlow<Map<BluetoothDevice.Address, PlayerState>>(emptyMap())
	private val requestedDeviceAddress = MutableStateFlow<BluetoothDevice.Address?>(null)

	private val bluetoothPermissionController = yuru.multiplePermissionController(
		buildList {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				add(Manifest.permission.BLUETOOTH_SCAN)
				add(Manifest.permission.BLUETOOTH_CONNECT)
			}
			else {
				add(Manifest.permission.ACCESS_FINE_LOCATION)
			}
		}
	)

	private val _state = MutableStateFlow(DeviceDiscoveryState())
	val state: StateFlow<DeviceDiscoveryState> = combineTuple(
		lapisBt.scannedDevices,
		lapisBt.pairedDevices,
		lapisBt.isScanning,
		lapisBt.state,
		bluetoothPermissionController.state,
		remotePlayerStates,
		lapisBt.activeBluetoothServersUuids,
		lapisBt.bluetoothDeviceName,
		playerRepository.state,
		requestedDeviceAddress,
		playerRepository.activeGameConfig,
		_state
	).map { (scanned, paired, scanning, btState, permissionStates, remotes, activeServers, localName, localPlayerStatus, requestedAddr, activeGame, localState) ->
		localState.copy(
			scannedDevices = scanned,
			pairedDevices = paired,
			isScanning = scanning,
			isBluetoothOn = btState.isOn,
			permissionStates = permissionStates,
			remotePlayerStates = remotes,
			isServerRunning = activeServers.contains(TIC_TAC_TOE_UUID),
			localDeviceName = localName,
			localPlayerState = localPlayerStatus,
			requestedDeviceAddress = requestedAddr,
			activeGameConfig = activeGame
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceDiscoveryState())

	private var invitationDeferred: CompletableDeferred<Boolean>? = null

	init {
		observeConnectedDevices()
		observeGameCleanup()
	}

	private fun observeGameCleanup() {
		viewModelScope.launch {
			combineTuple(
				playerRepository.state,
				remotePlayerStates,
				playerRepository.activeGameConfig
			).collect { (localState, remotes, activeConfig) ->
				if (activeConfig == null) return@collect

				val remoteState = remotes[activeConfig.deviceAddress] ?: PlayerState.Free
				if (localState != PlayerState.InGame && remoteState != PlayerState.InGame) {
					playerRepository.activeGameConfig.value = null
				}
			}
		}
	}

	private fun observeConnectedDevices() {
		viewModelScope.launch {
			lapisBt.connectedDevices.collect { connectedList ->
				val connectedAddresses = connectedList.map { it.address }.toSet()
				// Clean up states for devices that are no longer connected
				remotePlayerStates.update { currentMap ->
					currentMap.filterKeys { it in connectedAddresses }
				}

				connectedList.forEach { device ->
					// Register the ViewModel itself as the service implementation for all connected devices
					lapisBtRpc.registerBluetoothServerService(device.address, this@DeviceDiscoveryViewModel, InvitationService::class)

					// Observe remote player state
					observeRemotePlayerState(device.address)
				}
			}
		}
	}

	private fun observeRemotePlayerState(address: BluetoothDevice.Address) {
		viewModelScope.launch {
			val proxy = lapisBtRpc.getOrCreateBluetoothClientService(address, InvitationService::class)
			try {
				proxy.playerState().collect { playerState ->
					remotePlayerStates.update { it + (address to playerState) }
				}
			}
			catch (e: Exception) {
				// If it fails, maybe they don't have the service yet or disconnected
				remotePlayerStates.update { it - address }
			}
		}
	}

	override fun playerState(): Flow<PlayerState> = playerRepository.state.asStateFlow()

	override suspend fun requestGameInvitation(sessionId: String): InvitationResponse {
		println("$$$$ requestGameInvitation: $invitationDeferred")
		// If an invitation is already pending, return Busy
		if (invitationDeferred != null) return InvitationResponse.Busy
		if (playerRepository.state.value == PlayerState.InGame) return InvitationResponse.InGame

		val requestInfo = getLapisRequestInfo()
		val challengerAddress = requestInfo.deviceAddress
		val challenger = lapisBt.getRemoteDevice(challengerAddress)

		val deferred = CompletableDeferred<Boolean>()
		invitationDeferred = deferred

		try {
			playerRepository.state.value = PlayerState.Invited
			_state.update { it.copy(pendingInvitation = challenger) }

			val accepted = deferred.await()

			if (accepted) {
				playerRepository.state.value = PlayerState.InGame
				val config = GameConfig(
					deviceAddress = challengerAddress,
					isHost = false,
					sessionId = sessionId
				)
				playerRepository.activeGameConfig.value = config
				_state.update { it.copy(gameStarted = config) }
				return InvitationResponse.Accepted
			}

			playerRepository.state.value = PlayerState.Free
			return InvitationResponse.Rejected
		}
		finally {
			invitationDeferred = null
		}
	}

	fun sendAction(action: DeviceDiscoveryAction) {
		when (action) {
			DeviceDiscoveryAction.StartDiscovery -> startDiscovery()
			DeviceDiscoveryAction.StopDiscovery -> lapisBt.stopScan()
			is DeviceDiscoveryAction.Connect -> connect(action.device)
			is DeviceDiscoveryAction.Disconnect -> viewModelScope.launch { lapisBt.disconnectFromDevice(action.device.address) }
			is DeviceDiscoveryAction.CancelConnectionAttempt -> viewModelScope.launch { lapisBt.cancelConnectionAttempt(action.address) }
			DeviceDiscoveryAction.DismissLocationRationale -> _state.update { it.copy(showLocationRationale = false) }
			DeviceDiscoveryAction.OpenLocationSettings -> openLocationSettings()
			DeviceDiscoveryAction.MakeDiscoverable -> makeDiscoverable()
			DeviceDiscoveryAction.AcceptInvitation -> {
				invitationDeferred?.complete(true)
				_state.update { it.copy(pendingInvitation = null) }
			}
			DeviceDiscoveryAction.DeclineInvitation -> {
				invitationDeferred?.complete(false)
				_state.update { it.copy(pendingInvitation = null) }
				playerRepository.state.value = PlayerState.Free
			}
			is DeviceDiscoveryAction.RequestGame -> requestGame(action.device)
			is DeviceDiscoveryAction.RejoinGame -> {
				playerRepository.state.value = PlayerState.InGame
				_state.update { it.copy(gameStarted = action.config) }
			}
			DeviceDiscoveryAction.ResetNavigation -> {
				_state.update { it.copy(gameStarted = null) }
				if (playerRepository.state.value != PlayerState.InGame) {
					playerRepository.state.value = PlayerState.Free
				}
			}
		}
	}

	private fun startDiscovery() {
		viewModelScope.launch {
			if (!requestDiscoveryPermissions()) return@launch
			if (!ensureBluetoothEnabled()) return@launch

			val result = lapisBt.startScan()
			if (result is LapisBt.ScanResult.LocationDisabled) {
				_state.update { it.copy(showLocationRationale = true) }
			}
		}
	}

	private suspend fun ensureBluetoothEnabled(): Boolean {
		if (lapisBt.state.value.isOn) return true
		return activityResultBridge.launch(
			ActivityResultContracts.StartActivityForResult(),
			Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
		).resultCode == android.app.Activity.RESULT_OK
	}

	private suspend fun requestDiscoveryPermissions(): Boolean {
		val result = bluetoothPermissionController.request()
		return result.all { it.value == YuruPermissionState.Granted }
	}

	private fun connect(device: BluetoothDevice) {
		viewModelScope.launch {
			_state.update { it.copy(connectionStatus = ConnectionStatus.Connecting(device)) }

			val result = lapisBt.connectToDeviceWithoutPairing(device.address, TIC_TAC_TOE_UUID)

			when (result) {
				is LapisBt.ConnectionResult.ConnectionEstablished -> {
					_state.update { it.copy(connectionStatus = ConnectionStatus.Connected(device)) }
				}
				else -> {
					_state.update { it.copy(connectionStatus = ConnectionStatus.Error("Could not connect to ${device.name}")) }
				}
			}
		}
	}

	private fun requestGame(device: BluetoothDevice) {
		viewModelScope.launch {
			val proxy = lapisBtRpc.getOrCreateBluetoothClientService(device.address, InvitationService::class)

			playerRepository.state.value = PlayerState.Waiting
			requestedDeviceAddress.value = device.address

			val sessionId = java.util.UUID.randomUUID().toString()

			val response = try {
				proxy.requestGameInvitation(sessionId)
			}
			catch (e: Exception) {
				InvitationResponse.Rejected
			}
			finally {
				requestedDeviceAddress.value = null
			}

			when (response) {
				InvitationResponse.Accepted -> {
					playerRepository.state.value = PlayerState.InGame
					val config = GameConfig(
						deviceAddress = device.address,
						isHost = true,
						sessionId = sessionId
					)
					playerRepository.activeGameConfig.value = config
					_state.update {
						it.copy(
							connectionStatus = ConnectionStatus.Connected(device),
							gameStarted = config
						)
					}
				}
				InvitationResponse.Busy -> {
					playerRepository.state.value = PlayerState.Free
					_state.update { it.copy(connectionStatus = ConnectionStatus.Error("${device.name} is busy")) }
				}
				InvitationResponse.InGame -> {
					playerRepository.state.value = PlayerState.Free
					_state.update { it.copy(connectionStatus = ConnectionStatus.Error("${device.name} is already in a game")) }
				}
				InvitationResponse.Free -> {
					playerRepository.state.value = PlayerState.Free
					_state.update { it.copy(connectionStatus = ConnectionStatus.Error("${device.name} state mismatch")) }
				}
				InvitationResponse.Rejected -> {
					playerRepository.state.value = PlayerState.Free
					_state.update { it.copy(connectionStatus = ConnectionStatus.Error("Game request declined")) }
				}
			}
		}
	}

	private fun openLocationSettings() {
		viewModelScope.launch {
			_state.update { it.copy(showLocationRationale = false) }
			val result = activityResultBridge.launch(
				ActivityResultContracts.StartActivityForResult(),
				Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
			)

			val isLocationEnabled = result.resultCode == Activity.RESULT_OK
			if (isLocationEnabled) {
				startDiscovery()
			}
		}
	}

	private fun makeDiscoverable() {
		viewModelScope.launch {
			if (!ensureBluetoothEnabled()) return@launch
			activityResultBridge.launch(
				ActivityResultContracts.StartActivityForResult(),
				Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
					putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
				}
			)
		}
	}
}

data class DeviceDiscoveryState(
	val scannedDevices: List<ScannedBluetoothDevice> = emptyList(),
	val pairedDevices: List<BluetoothDevice> = emptyList(),
	val isScanning: Boolean = false,
	val isBluetoothOn: Boolean = false,
	val pairingRequired: Boolean = false,
	val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
	val showLocationRationale: Boolean = false,
	val pendingInvitation: BluetoothDevice? = null,
	val gameStarted: GameConfig? = null,
	val permissionStates: Map<String, YuruPermissionState> = emptyMap(),
	val remotePlayerStates: Map<BluetoothDevice.Address, PlayerState> = emptyMap(),
	val isServerRunning: Boolean = false,
	val localDeviceName: String? = null,
	val localPlayerState: PlayerState = PlayerState.Free,
	val requestedDeviceAddress: BluetoothDevice.Address? = null,
	val activeGameConfig: GameConfig? = null,
)

sealed interface ConnectionStatus {
	data object Disconnected : ConnectionStatus
	data class Connecting(val device: BluetoothDevice) : ConnectionStatus
	data class Connected(val device: BluetoothDevice) : ConnectionStatus
	data class Error(val message: String) : ConnectionStatus
}

sealed interface DeviceDiscoveryAction {
	data object StartDiscovery : DeviceDiscoveryAction
	data object StopDiscovery : DeviceDiscoveryAction
	data class Connect(val device: BluetoothDevice) : DeviceDiscoveryAction
	data class Disconnect(val device: BluetoothDevice) : DeviceDiscoveryAction
	data class CancelConnectionAttempt(val address: BluetoothDevice.Address) : DeviceDiscoveryAction
	data object DismissLocationRationale : DeviceDiscoveryAction
	data object OpenLocationSettings : DeviceDiscoveryAction
	data object MakeDiscoverable : DeviceDiscoveryAction
	data object AcceptInvitation : DeviceDiscoveryAction
	data object DeclineInvitation : DeviceDiscoveryAction
	data class RequestGame(val device: BluetoothDevice) : DeviceDiscoveryAction
	data class RejoinGame(val config: GameConfig) : DeviceDiscoveryAction
	data object ResetNavigation : DeviceDiscoveryAction
}
