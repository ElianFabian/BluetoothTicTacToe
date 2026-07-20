package com.elianfabian.bluetoothtictactoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.elianfabian.bluetoothtictactoe.ui.discovery.DeviceDiscoveryScreen
import com.elianfabian.bluetoothtictactoe.ui.game.GameScreen
import com.elianfabian.bluetoothtictactoe.ui.theme.BluetoothTicTacToeTheme
import com.elianfabian.lapisbt.model.BluetoothDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.get
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTicTacToeTheme {
                val backStack = rememberSaveable(saver = Nav3BackStackSaver) {
                    mutableStateListOf<Any>(Route.Discovery)
                }

                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        } else {
                            finish()
                        }
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = { key ->
                        when (key) {
                            is Route.Discovery -> NavEntry(key) {
                                DeviceDiscoveryScreen(
                                    viewModel = koinViewModel(),
                                    onNavigateToGame = { address, isHost, sessionId ->
                                        backStack.add(Route.Game(address.value, isHost, sessionId))
                                    }
                                )
                            }
                            is Route.Game -> NavEntry(key) {
                                val addr = BluetoothDevice.Address(key.address)
                                GameScreen(
                                    viewModel = koinViewModel { parametersOf(addr, key.isHost) },
                                    onNavigateBack = {
                                        if (backStack.lastOrNull() is Route.Game) {
                                            backStack.removeLastOrNull()
                                        }
                                    }
                                )
                            }
                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
        }
    }
}

@Serializable
sealed interface Route {
    @Serializable
    data object Discovery : Route
    @Serializable
    data class Game(
        val address: String,
        val isHost: Boolean,
        val sessionId: String
    ) : Route
}

val Nav3BackStackSaver = listSaver<SnapshotStateList<Any>, String>(
    save = { list -> list.map { Json.encodeToString(it as Route) } },
    restore = { restored ->
        mutableStateListOf<Any>().apply {
            addAll(restored.map { Json.decodeFromString<Route>(it) })
        }
    }
)
