package com.elianfabian.bluetoothtictactoe.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.bluetoothtictactoe.LapisBtProvider
import com.elianfabian.bluetoothtictactoe.data.Cell
import com.elianfabian.bluetoothtictactoe.data.GameState
import com.elianfabian.bluetoothtictactoe.data.GameStatus
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.bluetoothtictactoe.rpc.TicTacToeService
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.model.BluetoothDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(
	context: Context,
	private val opponentAddress: BluetoothDevice.Address,
	private val isHost: Boolean,
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

	// Service Proxy (to call the other device)
	private var serviceProxy: TicTacToeService? = null
	private var observationJob: Job? = null

	// Authoritative state (if Host)
	private val _gameState = MutableStateFlow(GameState())

	private val isActive = MutableStateFlow(false)

	init {
		playerRepository.state.value = PlayerState.InGame
		observeDisconnection()
		observeOpponentStatus()
		if (isHost) {
			_gameState.update { it.copy(status = GameStatus.Playing) }
			viewModelScope.launch {
				_gameState.collect { updateUIState(it) }
			}
		}
		println("$$$ GameViewModel created: $opponentAddress, host=$isHost")
	}

	fun setActive(active: Boolean) {
		isActive.value = active
		if (active) {
			setupServiceImplementation()
			if (!isHost) {
				startObservingHost()
			}
		}
		else {
			lapisBtRpc.unregisterBluetoothServerService(opponentAddress, TicTacToeService::class)
			observationJob?.cancel()
			observationJob = null
		}
	}

	private fun observeOpponentStatus() {
		viewModelScope.launch {
			val proxy = lapisBtRpc.getOrCreateBluetoothClientService(opponentAddress, com.elianfabian.bluetoothtictactoe.rpc.InvitationService::class)
			proxy.playerState().collect { opponentState ->
				val isOpponentInGame = opponentState == PlayerState.InGame
				
				if (isHost) {
					_gameState.update { current ->
						if (!isOpponentInGame && current.status == GameStatus.Playing) {
							current.copy(status = GameStatus.OpponentLeft)
						} else if (isOpponentInGame && current.status == GameStatus.OpponentLeft) {
							current.copy(status = GameStatus.Playing)
						} else {
							current
						}
					}
				} else {
					// Guest purely follows Host's gameState, but we can provide immediate local feedback
					if (!isOpponentInGame && _state.value.gameStatus == GameStatus.Playing) {
						_state.update { it.copy(gameStatus = GameStatus.OpponentLeft) }
					} else if (isOpponentInGame && _state.value.gameStatus == GameStatus.OpponentLeft) {
						// The UI will update when the next gameState arrives, but we can optimisticlly reset status
						_state.update { it.copy(gameStatus = GameStatus.Playing) }
					}
				}
			}
		}
	}

	override fun onCleared() {
		playerRepository.state.value = PlayerState.Free
		lapisBtRpc.unregisterBluetoothServerService(opponentAddress, TicTacToeService::class)

		println("$$$ GameViewModel onCleared: $opponentAddress")
	}

	private fun setupServiceImplementation() {
		val impl = object : TicTacToeService {
			override fun gameState(): Flow<GameState> {
				return if (_state.value.isHost) _gameState.asStateFlow() else emptyFlow()
			}

			override suspend fun makeMove(row: Int, col: Int): Boolean {
				return if (_state.value.isHost) handleMove(row, col) else false
			}

			override suspend fun restartGame(): Boolean {
				return if (_state.value.isHost) {
					this@GameViewModel.restartGame()
					true
				}
				else false
			}
		}
		//runCatching {
			lapisBtRpc.registerBluetoothServerService(opponentAddress, impl, TicTacToeService::class)
		//}
		serviceProxy = lapisBtRpc.getOrCreateBluetoothClientService(opponentAddress, TicTacToeService::class)
	}

	private fun startObservingHost() {
		if (observationJob != null) return
		observationJob = viewModelScope.launch {
			serviceProxy?.gameState()?.collect { gameState ->
				updateUIState(gameState)
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
		when (action) {
			is GameAction.PlaceMove -> {
				if (_state.value.isHost) {
					handleMove(action.row, action.col)
				}
				else {
					viewModelScope.launch {
						serviceProxy?.makeMove(action.row, action.col)
					}
				}
			}
			GameAction.RestartGame -> restartGame()
			GameAction.LeaveGame -> {
				if (_gameState.value.status == GameStatus.OpponentLeft || _gameState.value.status == GameStatus.OpponentDisconnected) {
					playerRepository.clearActiveGame()
				}
			}
		}
	}

	private fun handleMove(row: Int, col: Int): Boolean {
		val current = _gameState.value
		if (current.status != GameStatus.Playing) return false
		if (current.board[row][col] != Cell.Empty) return false

		val newBoard = current.board.mapIndexed { r, rows ->
			rows.mapIndexed { c, cell ->
				if (r == row && c == col) current.currentTurn else cell
			}
		}

		val winner = checkWinner(newBoard)
		val isDraw = winner == Cell.Empty && newBoard.all { it.all { cell -> cell != Cell.Empty } }

		val nextStatus = if (winner != Cell.Empty || isDraw) GameStatus.Finished else GameStatus.Playing
		val nextTurn = if (current.currentTurn == Cell.X) Cell.O else Cell.X

		_gameState.update {
			it.copy(
				board = newBoard,
				currentTurn = nextTurn,
				status = nextStatus,
				winner = if (winner != Cell.Empty) winner else null,
				isDraw = isDraw
			)
		}
		return true
	}

	private fun checkWinner(board: List<List<Cell>>): Cell {
		for (i in 0..2) {
			if (board[i][0] != Cell.Empty && board[i][0] == board[i][1] && board[i][1] == board[i][2]) return board[i][0]
			if (board[0][i] != Cell.Empty && board[0][i] == board[1][i] && board[1][i] == board[2][i]) return board[0][i]
		}
		if (board[0][0] != Cell.Empty && board[0][0] == board[1][1] && board[1][1] == board[2][2]) return board[0][0]
		if (board[0][2] != Cell.Empty && board[0][2] == board[1][1] && board[1][1] == board[2][0]) return board[0][2]
		return Cell.Empty
	}

	private fun restartGame() {
		if (_state.value.isHost) {
			_gameState.value = GameState(status = GameStatus.Playing)
		}
		else {
			viewModelScope.launch {
				serviceProxy?.restartGame()
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
