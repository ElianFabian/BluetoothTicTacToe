package com.elianfabian.bluetoothtictactoe.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.ui.theme.BluetoothTicTacToeTheme
import com.elianfabian.lapisbt.model.BluetoothDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    viewModel: DeviceDiscoveryViewModel,
    onNavigateToGame: (BluetoothDevice.Address, Boolean, String) -> Unit,
) {
	val state by viewModel.state.collectAsState()
	DeviceDiscoveryContent(
		state = state,
		onAction = viewModel::sendAction,
		onNavigateToGame = onNavigateToGame
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryContent(
    state: DeviceDiscoveryState,
    onAction: (DeviceDiscoveryAction) -> Unit,
    onNavigateToGame: (BluetoothDevice.Address, Boolean, String) -> Unit,
) {
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(state.gameStarted) {
		state.gameStarted?.let { config ->
			onNavigateToGame(config.deviceAddress, config.isHost, config.sessionId)
			onAction(DeviceDiscoveryAction.ResetNavigation)
		}
	}

	LaunchedEffect(state.connectionStatus) {
		when (val status = state.connectionStatus) {
			is ConnectionStatus.Error -> snackbarHostState.showSnackbar(status.message)
			else -> Unit
		}
	}

	if (state.pendingInvitation != null) {
		InvitationDialog(
			opponentName = state.pendingInvitation.name ?: "Unknown",
			onAccept = { onAction(DeviceDiscoveryAction.AcceptInvitation) },
			onDecline = { onAction(DeviceDiscoveryAction.DeclineInvitation) }
		)
	}

	if (state.showLocationRationale) {
		AlertDialog(
			onDismissRequest = { onAction(DeviceDiscoveryAction.DismissLocationRationale) },
			title = { Text("Location Required") },
			text = { Text("On your version of Android, Location Services must be enabled to scan for Bluetooth devices. Please enable it in Settings.") },
			confirmButton = {
				Button(onClick = { onAction(DeviceDiscoveryAction.OpenLocationSettings) }) {
					Text("Open Settings")
				}
			},
			dismissButton = {
				TextButton(onClick = { onAction(DeviceDiscoveryAction.DismissLocationRationale) }) {
					Text("Cancel")
				}
			}
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Bluetooth Tic Tac Toe") }
			)
		},
		floatingActionButton = {
			ExtendedFloatingActionButton(
				onClick = {
					if (state.isScanning) {
						onAction(DeviceDiscoveryAction.StopDiscovery)
					}
					else {
						onAction(DeviceDiscoveryAction.StartDiscovery)
					}
				},
				icon = {
					Icon(
						imageVector = if (state.isScanning) Icons.Default.Close else Icons.Default.Search,
						contentDescription = null
					)
				},
				text = {
					Text(text = if (state.isScanning) "Stop Scanning" else "Scan for Devices")
				}
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) }
	) { padding ->
		Column(
			modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
		) {
			BluetoothStatusHeader(
				isBluetoothOn = state.isBluetoothOn,
				isScanning = state.isScanning,
				localDeviceName = state.localDeviceName,
				onMakeDiscoverable = { onAction(DeviceDiscoveryAction.MakeDiscoverable) }
			)

			HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

			LazyColumn(
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 88.dp)
			) {
				state.activeDevice?.let { device ->
					val isActive = state.localPlayerState == PlayerState.InGame || state.localPlayerState == PlayerState.InGamePaused
					if (isActive) {
						item { SectionHeader("Current Session") }
						item {
							DeviceItem(
								device = device,
								remotePlayerState = state.remotePlayerStates[device.address] ?: PlayerState.Free,
								localPlayerState = state.localPlayerState,
								isRequested = false,
								isInviter = false,
								canRejoin = true,
								isConnecting = state.connectionStatus is ConnectionStatus.Connecting && state.connectionStatus.device.address == device.address,
								onClick = {
									val status = state.connectionStatus
									if (status is ConnectionStatus.Connecting && status.device.address == device.address) {
										onAction(DeviceDiscoveryAction.CancelConnectionAttempt(device.address))
									} else {
										onAction(DeviceDiscoveryAction.Connect(device))
									}
								},
								onPlayClick = { onAction(DeviceDiscoveryAction.RequestGame(device)) },
								onRejoinClick = { state.activeGameConfig?.let { onAction(DeviceDiscoveryAction.RejoinGame(it)) } },
								onDisconnectClick = { onAction(DeviceDiscoveryAction.Disconnect(device)) }
							)
						}
						item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
					}
				}

				if (state.pairedDevices.isNotEmpty()) {
					item { SectionHeader("Paired Devices") }
					items(state.pairedDevices) { device ->
						val isActive = state.localPlayerState == PlayerState.InGame || state.localPlayerState == PlayerState.InGamePaused
						if (device.address == state.activeGameConfig?.deviceAddress && isActive) return@items

						val canRejoin = state.activeGameConfig?.deviceAddress == device.address &&
								state.localPlayerState == PlayerState.InGamePaused

						DeviceItem(
							device = device,
							remotePlayerState = state.remotePlayerStates[device.address] ?: PlayerState.Free,
							localPlayerState = state.localPlayerState,
							isRequested = state.requestedDeviceAddress == device.address,
							isInviter = state.pendingInvitation?.address == device.address,
							canRejoin = canRejoin,
							isConnecting = state.connectionStatus is ConnectionStatus.Connecting && state.connectionStatus.device.address == device.address,
							onClick = {
								val status = state.connectionStatus
								if (status is ConnectionStatus.Connecting && status.device.address == device.address) {
									onAction(DeviceDiscoveryAction.CancelConnectionAttempt(device.address))
								}
								else {
									onAction(DeviceDiscoveryAction.Connect(device))
								}
							},
							onPlayClick = { onAction(DeviceDiscoveryAction.RequestGame(device)) },
							onRejoinClick = { state.activeGameConfig?.let { onAction(DeviceDiscoveryAction.RejoinGame(it)) } },
							onDisconnectClick = { onAction(DeviceDiscoveryAction.Disconnect(device)) }
						)
					}
				}

				if (state.scannedDevices.isNotEmpty()) {
					item { SectionHeader("Discovered Devices") }
					items(state.scannedDevices) { scanned ->
						val device = scanned.device
						val isActive = state.localPlayerState == PlayerState.InGame || state.localPlayerState == PlayerState.InGamePaused
						if (device.address == state.activeGameConfig?.deviceAddress && isActive) return@items

						val canRejoin = state.activeGameConfig?.deviceAddress == device.address &&
								state.localPlayerState == PlayerState.InGamePaused

						DeviceItem(
							device = device,
							remotePlayerState = state.remotePlayerStates[device.address] ?: PlayerState.Free,
							localPlayerState = state.localPlayerState,
							isRequested = state.requestedDeviceAddress == device.address,
							isInviter = state.pendingInvitation?.address == device.address,
							canRejoin = canRejoin,
							isConnecting = state.connectionStatus is ConnectionStatus.Connecting && state.connectionStatus.device.address == device.address,
							onClick = {
								val status = state.connectionStatus
								if (status is ConnectionStatus.Connecting && status.device.address == device.address) {
									onAction(DeviceDiscoveryAction.CancelConnectionAttempt(device.address))
								}
								else {
									onAction(DeviceDiscoveryAction.Connect(device))
								}
							},
							onPlayClick = { onAction(DeviceDiscoveryAction.RequestGame(device)) },
							onRejoinClick = { state.activeGameConfig?.let { onAction(DeviceDiscoveryAction.RejoinGame(it)) } },
							onDisconnectClick = { onAction(DeviceDiscoveryAction.Disconnect(device)) }
						)
					}
				}

				if (state.pairedDevices.isEmpty() && state.scannedDevices.isEmpty()) {
					item {
						Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
							Text(
								text = if (state.isScanning) "Searching for devices..." else "No devices found",
								style = MaterialTheme.typography.bodyMedium
							)
						}
					}
				}
			}
		}
	}
}

@Composable
fun BluetoothStatusHeader(
    isBluetoothOn: Boolean,
    isScanning: Boolean,
    localDeviceName: String?,
    onMakeDiscoverable: () -> Unit,
) {
	Column {
		if (localDeviceName != null) {
			Text(
				text = "My Device: $localDeviceName",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(bottom = 8.dp)
			)
		}
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth()
		) {
			Icon(
				imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
				contentDescription = null,
				tint = if (isBluetoothOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
			)
			Spacer(modifier = Modifier.width(8.dp))
			Text(
				text = if (isBluetoothOn) "Bluetooth is ON" else "Bluetooth is OFF",
				style = MaterialTheme.typography.titleMedium,
				modifier = Modifier.weight(1f)
			)
			if (isScanning) {
				CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
				Spacer(modifier = Modifier.width(8.dp))
			}
			Button(onClick = onMakeDiscoverable, modifier = Modifier.padding(start = 8.dp)) {
				Text("Make Discoverable", style = MaterialTheme.typography.labelSmall)
			}
		}
	}
}

@Composable
fun SectionHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.labelLarge,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(vertical = 8.dp)
	)
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    remotePlayerState: PlayerState,
    localPlayerState: PlayerState,
    isRequested: Boolean,
    isInviter: Boolean,
    canRejoin: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onRejoinClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
	Card(
		modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
	) {
		Row(
			modifier = Modifier.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				imageVector = if (isConnecting) Icons.Default.Close else Icons.Default.BluetoothConnected,
				contentDescription = null
			)
			if (isConnecting) {
				Spacer(modifier = Modifier.width(8.dp))
				CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
			}
			Spacer(modifier = Modifier.width(16.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge)
				Text(text = device.address.toString(), style = MaterialTheme.typography.bodySmall)
				if (device.connectionState == BluetoothDevice.ConnectionState.Connected) {
					val statusText = when {
						isRequested -> "Waiting..."
						isInviter -> "Invited you"
						remotePlayerState == PlayerState.InGamePaused -> "In Game (Paused)"
						else -> remotePlayerState.name
					}
					Text(
						text = "Status: $statusText",
						style = MaterialTheme.typography.bodySmall,
						color = when {
							isRequested || isInviter -> Color(0xFFFF9800)
							remotePlayerState == PlayerState.Free -> Color(0xFF4CAF50)
							remotePlayerState == PlayerState.Invited -> Color(0xFFFF9800)
							remotePlayerState == PlayerState.Waiting -> Color(0xFFFF9800)
							remotePlayerState == PlayerState.InGame -> Color(0xFF2196F3)
							remotePlayerState == PlayerState.InGamePaused -> Color(0xFF2196F3)
							else -> Color.Unspecified
						}
					)
				}
				if (isConnecting) {
					Text(
						text = "Connecting... (Tap to cancel)",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}
			if (device.connectionState == BluetoothDevice.ConnectionState.Connected) {
				IconButton(onClick = onDisconnectClick) {
					Icon(Icons.Default.Close, contentDescription = "Disconnect")
				}
				Spacer(modifier = Modifier.width(8.dp))
				if (canRejoin) {
					Button(
						onClick = onRejoinClick,
						enabled = localPlayerState == PlayerState.InGamePaused
					) {
						Text("Rejoin", style = MaterialTheme.typography.labelSmall)
					}
				}
				else {
					Button(
						onClick = onPlayClick,
						enabled = remotePlayerState == PlayerState.Free && localPlayerState == PlayerState.Free
					) {
						Text("Play", style = MaterialTheme.typography.labelSmall)
					}
				}
			}
		}
	}
}

@Composable
fun InvitationDialog(
    opponentName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = { /* No-op */ },
		title = { Text("Game Invitation") },
		text = { Text("$opponentName wants to start a new game with you!") },
		confirmButton = {
			Button(onClick = onAccept) { Text("Accept") }
		},
		dismissButton = {
			TextButton(onClick = onDecline) { Text("Decline") }
		}
	)
}

@Preview(showBackground = true)
@Composable
fun DeviceDiscoveryContentPreview() {
	BluetoothTicTacToeTheme {
		DeviceDiscoveryContent(
			state = DeviceDiscoveryState(
				isBluetoothOn = true,
				isScanning = true,
				localDeviceName = "Pixel 8 Pro"
			),
			onAction = {},
			onNavigateToGame = { _, _, _ -> }
		)
	}
}
