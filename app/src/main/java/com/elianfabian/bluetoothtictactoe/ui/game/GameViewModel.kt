package com.elianfabian.bluetoothtictactoe.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.bluetoothtictactoe.LapisBtProvider
import com.elianfabian.bluetoothtictactoe.data.Cell
import com.elianfabian.bluetoothtictactoe.data.GameState
import com.elianfabian.bluetoothtictactoe.data.GameStatus
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.data.TicTacToeDataSource
import com.elianfabian.bluetoothtictactoe.rpc.TicTacToeService
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.model.BluetoothDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(
	context: Context,
	private val opponentAddress: BluetoothDevice.Address,
	private val isHost: Boolean,
	private val dataSource: TicTacToeDataSource
) : ViewModel() {

	private val lapisBt = LapisBtProvider.getLapisBt(context)
	private val lapisBtRpc = LapisBtProvider.getLapisBtRpc(context)
	private val playerRepository = LapisBtProvider.getPlayerRepository(context)
	private val opponent = lapisBt.getRemoteDevice(opponentAddress)

	private val _state = MutableStateFlow(
		GameUIState(
			opponentName = opponent.name ?: "Opponent",
			isHost = isHost,
			mySymbol = if (isHost) Cell.X else Cell.O
		)
	)
	val state: StateFlow<GameUIState> = _state.asStateFlow()


	init {
		playerRepository.state.value = PlayerState.InGame
		observeDisconnection()
		observeOpponentStatus()
		setupServiceImplementation()
		startGameStateObservation()

		println("$$$ GameViewModel created: $opponentAddress, host=$isHost")
	}

	private fun startGameStateObservation() {
		viewModelScope.launch {
			dataSource.gameState.collect { updateUIState(it) }
		}
	}

	private fun observeOpponentStatus() {
		viewModelScope.launch {
			val proxy = lapisBtRpc.getOrCreateBluetoothClientService(opponentAddress, com.elianfabian.bluetoothtictactoe.rpc.InvitationService::class)
			proxy.playerState().collect { opponentState ->
				val isOpponentInGame = opponentState == PlayerState.InGame
				
				if (!isOpponentInGame && _state.value.gameStatus == GameStatus.Playing) {
					_state.update { it.copy(gameStatus = GameStatus.OpponentLeft) }
				} else if (isOpponentInGame && _state.value.gameStatus == GameStatus.OpponentLeft) {
					_state.update { it.copy(gameStatus = GameStatus.Playing) }
				}
			}
		}
	}

	override fun onCleared() {
		playerRepository.state.value = PlayerState.Free
		lapisBtRpc.unregisterBluetoothServerService(opponentAddress, TicTacToeService::class)
	}

	private fun setupServiceImplementation() {
		if (isHost) {
			val impl = object : TicTacToeService {
				override fun gameState(): Flow<GameState> = dataSource.gameState
				override suspend fun makeMove(row: Int, col: Int): Boolean = dataSource.makeMove(row, col)
				override suspend fun restartGame(): Boolean = dataSource.restartGame()
			}
			lapisBtRpc.registerBluetoothServerService(opponentAddress, impl, TicTacToeService::class)
		}
	}

	private fun updateUIState(gameState: GameState) {
		_state.update {
			it.copy(
				board = gameState.board,
				currentTurn = gameState.currentTurn,
				gameStatus = gameState.status,
				winner = gameState.winner,
				isDraw = gameState.isDraw
			)
		}
	}

	fun sendAction(action: GameAction) {
		viewModelScope.launch {
			when (action) {
				is GameAction.PlaceMove -> {
					dataSource.makeMove(action.row, action.col)
				}
				GameAction.RestartGame -> {
					dataSource.restartGame()
				}
				GameAction.LeaveGame -> {
					if (_state.value.gameStatus == GameStatus.OpponentLeft || _state.value.gameStatus == GameStatus.OpponentDisconnected) {
						playerRepository.clearActiveGame()
					}
				}
			}
		}
	}

	private fun observeDisconnection() {
		viewModelScope.launch {
			lapisBt.events.collect { event ->
				if (event is LapisBt.Event.OnDeviceDisconnected && event.device.address == opponentAddress) {
					_state.update { it.copy(gameStatus = GameStatus.OpponentDisconnected) }
				}
			}
		}
	}
}

data class GameUIState(
	val board: List<List<Cell>> = List(3) { List(3) { Cell.Empty } },
	val isHost: Boolean = false,
	val mySymbol: Cell = Cell.X,
	val currentTurn: Cell = Cell.X,
	val gameStatus: GameStatus = GameStatus.Waiting,
	val winner: Cell? = null,
	val isDraw: Boolean = false,
	val opponentName: String = "Opponent",
)

sealed interface GameAction {
	data class PlaceMove(val row: Int, val col: Int) : GameAction
	data object RestartGame : GameAction
	data object LeaveGame : GameAction
}
