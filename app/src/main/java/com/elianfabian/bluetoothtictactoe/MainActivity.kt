package com.elianfabian.bluetoothtictactoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.ui.discovery.DeviceDiscoveryScreen
import com.elianfabian.bluetoothtictactoe.ui.discovery.DeviceDiscoveryViewModel
import com.elianfabian.bluetoothtictactoe.ui.game.GameScreen
import com.elianfabian.bluetoothtictactoe.ui.game.GameViewModel
import com.elianfabian.bluetoothtictactoe.ui.theme.BluetoothTicTacToeTheme
import com.elianfabian.lapisbt.model.BluetoothDevice

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LapisBtProvider.getLapisBt(this)
        enableEdgeToEdge()
        setContent {
            BluetoothTicTacToeTheme {
                val context = LocalContext.current
                var currentScreen by rememberSaveable(saver = ScreenSaver) { 
                    mutableStateOf<Screen>(Screen.Discovery) 
                }

                LaunchedEffect(Unit) {
                    // We removed the automatic server start. It's now handled by the ViewModel via UI
                }

                when (val screen = currentScreen) {
                    Screen.Discovery -> {
                        val discoveryViewModel: DeviceDiscoveryViewModel = viewModel(
                            factory = GenericViewModelFactory { DeviceDiscoveryViewModel(context) }
                        )
                        DeviceDiscoveryScreen(
                            viewModel = discoveryViewModel,
                            onNavigateToGame = { address, isHost, sessionId ->
                                currentScreen = Screen.Game(address, isHost, sessionId)
                            }
                        )
                    }
                    is Screen.Game -> {
                        val gameViewModel: GameViewModel = viewModel(
                            key = screen.address.value + "_" + screen.sessionId,
                            factory = GenericViewModelFactory {
                                GameViewModel(context, screen.address, screen.isHost)
                            }
                        )
                        GameScreen(
                            viewModel = gameViewModel,
                            onNavigateBack = { 
                                LapisBtProvider.getPlayerRepository(applicationContext).state.value = PlayerState.Free
                                currentScreen = Screen.Discovery 
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        // Shared UUID is now managed in ViewModel/Provider
    }
}

val ScreenSaver = Saver<MutableState<Screen>, Any>(
    save = { state ->
        when (val screen = state.value) {
            Screen.Discovery -> listOf("Discovery")
            is Screen.Game -> listOf("Game", screen.address.value, screen.isHost, screen.sessionId)
        }
    },
    restore = { value ->
        val list = value as List<*>
        val screen = when (list[0]) {
            "Discovery" -> Screen.Discovery
            "Game" -> Screen.Game(
                address = BluetoothDevice.Address(list[1] as String),
                isHost = list[2] as Boolean,
                sessionId = list[3] as String
            )
            else -> Screen.Discovery
        }
        mutableStateOf(screen)
    }
)

sealed interface Screen {
    data object Discovery : Screen
    data class Game(
        val address: BluetoothDevice.Address, 
        val isHost: Boolean,
        val sessionId: String = java.util.UUID.randomUUID().toString()
    ) : Screen
}

class GenericViewModelFactory<T : androidx.lifecycle.ViewModel>(
    private val creator: () -> T
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return creator() as T
    }
}
