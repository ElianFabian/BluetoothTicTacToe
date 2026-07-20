package com.elianfabian.bluetoothtictactoe.data

import com.elianfabian.bluetoothtictactoe.rpc.TicTacToeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface TicTacToeDataSource {
    val gameState: Flow<GameState>
    suspend fun makeMove(row: Int, col: Int): Boolean
    suspend fun restartGame(): Boolean
}

class LocalTicTacToeDataSource : TicTacToeDataSource {
    private val _gameState = MutableStateFlow(GameState(status = GameStatus.Playing))
    override val gameState: Flow<GameState> = _gameState.asStateFlow()

    override suspend fun makeMove(row: Int, col: Int): Boolean {
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

        _gameState.update { it.copy(
            board = newBoard,
            currentTurn = nextTurn,
            status = nextStatus,
            winner = if (winner != Cell.Empty) winner else null,
            isDraw = isDraw
        ) }
        return true
    }

    override suspend fun restartGame(): Boolean {
        _gameState.value = GameState(status = GameStatus.Playing)
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
}

class RemoteTicTacToeDataSource(
    private val serviceProxy: TicTacToeService
) : TicTacToeDataSource {
    override val gameState: Flow<GameState> = serviceProxy.gameState()

    override suspend fun makeMove(row: Int, col: Int): Boolean {
        return serviceProxy.makeMove(row, col)
    }

    override suspend fun restartGame(): Boolean {
        return serviceProxy.restartGame()
    }
}
