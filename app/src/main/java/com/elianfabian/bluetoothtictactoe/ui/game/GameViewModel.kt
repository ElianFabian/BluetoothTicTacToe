package com.elianfabian.bluetoothtictactoe.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.bluetoothtictactoe.data.Cell
import com.elianfabian.bluetoothtictactoe.data.GameSessionManager
import com.elianfabian.bluetoothtictactoe.data.GameState
import com.elianfabian.bluetoothtictactoe.data.GameStatus
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.rpc.InvitationService
import com.elianfabian.bluetoothtictactoe.rpc.TicTacToeService
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.model.BluetoothDevice
import com.elianfabian.lapisbt_rpc.LapisBtRpc
import com.elianfabian.lapisbt_rpc.getOrCreateBluetoothClientService
import com.elianfabian.lapisbt_rpc.registerBluetoothServerService
import com.elianfabian.lapisbt_rpc.unregisterBluetoothServerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(
	private val lapisBt: LapisBt,
	private val lapisBtRpc: LapisBtRpc,
	private val sessionManager: GameSessionManager,
	private val opponentAddress: BluetoothDevice.Address,
	private val isHost: Boolean,
) : ViewModel() {

	private val opponent = lapisBt.getRemoteDevice(opponentAddress)
	private val dataSource = sessionManager.getOrCreateDataSource(opponentAddress, isHost)

	private val _state = MutableStateFlow(
		GameUIState(
			opponentName = opponent.name ?: "Opponent",
			isHost = isHost,
			mySymbol = if (isHost) Cell.X else Cell.O
		)
	)
	val state: StateFlow<GameUIState> = _state.asStateFlow()


	init {
		sessionManager.state.value = PlayerState.InGame

		// Initial connection check
		if (opponent.connectionState != BluetoothDevice.ConnectionState.Connected) {
			_state.update { it.copy(gameStatus = GameStatus.OpponentDisconnected) }
		}

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
			val proxy = lapisBtRpc.getOrCreateBluetoothClientService<InvitationService>(opponentAddress)
			proxy.playerState().collect { opponentState ->
				val isOpponentInGame = opponentState == PlayerState.InGame

				if (!isOpponentInGame && _state.value.gameStatus == GameStatus.Playing) {
					_state.update { it.copy(gameStatus = GameStatus.OpponentLeft) }
				}
				else if (isOpponentInGame && _state.value.gameStatus == GameStatus.OpponentLeft) {
					_state.update { it.copy(gameStatus = GameStatus.Playing) }
				}
			}
		}
	}

	override fun onCleared() {
		if (_state.value.gameStatus == GameStatus.Playing || _state.value.gameStatus == GameStatus.Waiting) {
			sessionManager.state.value = PlayerState.InGamePaused
		}
		else {
			sessionManager.state.value = PlayerState.Free

			if (isHost) {
				lapisBtRpc.unregisterBluetoothServerService<TicTacToeService>(opponentAddress)
			}
		}
	}

	private fun setupServiceImplementation() {
		if (isHost) {
			val impl = object : TicTacToeService {
				override fun gameState(): Flow<GameState> = dataSource.gameState
				override suspend fun makeMove(row: Int, col: Int): Boolean = dataSource.makeMove(row, col)
				override suspend fun restartGame(): Boolean = dataSource.restartGame()
			}

			// TODO: Maybe we should add a method to check if a service is already registered
			runCatching {
				lapisBtRpc.registerBluetoothServerService<TicTacToeService>(opponentAddress, impl)
			}
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
						sessionManager.clearActiveGame()
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
