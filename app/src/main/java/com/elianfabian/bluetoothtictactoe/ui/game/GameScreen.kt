package com.elianfabian.bluetoothtictactoe.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.elianfabian.bluetoothtictactoe.data.Cell
import com.elianfabian.bluetoothtictactoe.data.GameStatus

import androidx.compose.ui.tooling.preview.Preview
import com.elianfabian.bluetoothtictactoe.ui.discovery.InvitationDialog
import com.elianfabian.bluetoothtictactoe.ui.theme.BluetoothTicTacToeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    GameContent(
        state = state,
        onAction = viewModel::sendAction,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameContent(
    state: GameUIState,
    onAction: (GameAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showExitConfirmation by remember { mutableStateOf(false) }

    val handleBack = {
        if (state.gameStatus == GameStatus.OpponentLeft) {
            showExitConfirmation = true
        } else {
            onAction(GameAction.LeaveGame)
            onNavigateBack()
        }
    }

    BackHandler(onBack = handleBack)

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("End Game Session?") },
            text = { Text("Your opponent has already left to the discovery screen. If you leave now, the game session will be permanently cleared and you won't be able to rejoin.") },
            confirmButton = {
                Button(onClick = {
                    showExitConfirmation = false
                    onAction(GameAction.LeaveGame)
                    onNavigateBack()
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Stay")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tic Tac Toe") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                StatusIndicator(state)

                Spacer(modifier = Modifier.size(32.dp))

                Board(
                    board = state.board,
                    onCellClick = { r, c -> onAction(GameAction.PlaceMove(r, c)) },
                    enabled = state.gameStatus == GameStatus.Playing && state.currentTurn == state.mySymbol
                )

                if (state.gameStatus == GameStatus.Finished) {
                    Spacer(modifier = Modifier.size(32.dp))
                    Button(onClick = { onAction(GameAction.RestartGame) }) {
                        Text("Restart Game")
                    }
                }

                if (state.gameStatus == GameStatus.OpponentLeft || state.gameStatus == GameStatus.OpponentDisconnected) {
                    Spacer(modifier = Modifier.size(32.dp))
                    Button(onClick = handleBack) {
                        Text("Return to Discovery")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(state: GameUIState) {
	val text = when (state.gameStatus) {
		GameStatus.Waiting -> "Waiting for game request..."
		GameStatus.Playing -> if (state.currentTurn == state.mySymbol) "Your turn (${state.mySymbol})" else "Opponent's turn"
		GameStatus.Finished -> when {
			state.winner == state.mySymbol -> "You won! 🎉"
			state.winner != null -> "Opponent won! 😔"
			state.isDraw -> "It's a draw! 🤝"
			else -> "Game Over"
		}
		GameStatus.OpponentDisconnected -> "Opponent disconnected 🔌"
		GameStatus.OpponentLeft -> "Opponent left the match 🚪"
	}

	AnimatedContent(
		targetState = text,
		transitionSpec = {
			(fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
				.togetherWith(fadeOut(animationSpec = tween(90)))
		},
		label = "StatusAnimation"
	) { targetText ->
		Text(
			text = targetText,
			style = MaterialTheme.typography.headlineMedium,
			color = if (state.gameStatus == GameStatus.OpponentDisconnected || state.gameStatus == GameStatus.OpponentLeft) MaterialTheme.colorScheme.error else Color.Unspecified
		)
	}
}

@Composable
fun Board(
    board: List<List<Cell>>,
    onCellClick: (Int, Int) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (r in 0..2) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in 0..2) {
                    CellItem(
                        cell = board[r][c],
                        onClick = { if (enabled) onCellClick(r, c) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun CellItem(
    cell: Cell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = cell == Cell.Empty) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (cell) {
                Cell.X -> Cross(modifier = Modifier.size(64.dp))
                Cell.O -> Nought(modifier = Modifier.size(64.dp))
                Cell.Empty -> Unit
            }
        }
    }
}

@Composable
fun Cross(modifier: Modifier = Modifier) {
	val color = MaterialTheme.colorScheme.primary
	val animProgress = remember { Animatable(0f) }

	LaunchedEffect(Unit) {
		animProgress.animateTo(
			targetValue = 1f,
			animationSpec = tween(durationMillis = 300, easing = LinearEasing)
		)
	}

	Canvas(modifier = modifier.padding(16.dp)) {
		val progress = animProgress.value
		// First line \
		if (progress > 0f) {
			val firstLineProgress = minOf(progress * 2, 1f)
			drawLine(
				color = color,
				start = Offset(0f, 0f),
				end = Offset(size.width * firstLineProgress, size.height * firstLineProgress),
				strokeWidth = 8.dp.toPx(),
				cap = StrokeCap.Round
			)
		}
		// Second line /
		if (progress > 0.5f) {
			val secondLineProgress = (progress - 0.5f) * 2
			drawLine(
				color = color,
				start = Offset(size.width, 0f),
				end = Offset(size.width - (size.width * secondLineProgress), size.height * secondLineProgress),
				strokeWidth = 8.dp.toPx(),
				cap = StrokeCap.Round
			)
		}
	}
}

@Composable
fun Nought(modifier: Modifier = Modifier) {
	val color = MaterialTheme.colorScheme.secondary
	val animProgress = remember { Animatable(0f) }

	LaunchedEffect(Unit) {
		animProgress.animateTo(
			targetValue = 1f,
			animationSpec = tween(durationMillis = 400, easing = LinearEasing)
		)
	}

	Canvas(modifier = modifier.padding(16.dp)) {
		drawArc(
			color = color,
			startAngle = -90f,
			sweepAngle = 360f * animProgress.value,
			useCenter = false,
			style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
			size = Size(size.width, size.height)
		)
	}
}

@Preview(showBackground = true)
@Composable
private fun GameContentPreview() {
    BluetoothTicTacToeTheme {
        GameContent(
            state = GameUIState(
                board = listOf(
                    listOf(Cell.X, Cell.O, Cell.Empty),
                    listOf(Cell.Empty, Cell.X, Cell.Empty),
                    listOf(Cell.Empty, Cell.Empty, Cell.O)
                ),
                mySymbol = Cell.X,
                currentTurn = Cell.X,
                gameStatus = GameStatus.Playing,
                opponentName = "Alice"
            ),
            onAction = {},
            onNavigateBack = {}
        )
    }
}
