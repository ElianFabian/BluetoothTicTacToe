# Bluetooth Tic Tac Toe Detailed Implementation Plan

Build a Bluetooth Classic Tic Tac Toe game using `LapisBt`, `YuruPermissions`, and `ActivityResultBridge`.

## User Review Required

> [!IMPORTANT]
> **Game State Architecture**: **Master-Observer** pattern.
> - **Host (Acceptor)**: Implements `TicTacToeService` and holds the `GameState`.
> - **Guest (Initiator)**: Calls methods on the `TicTacToeService` proxy.

> [!NOTE]
> **Shared Service**: Every device will run a "Lobby" RPC service to handle game invitations before the actual Tic Tac Toe service starts.

## Proposed Changes

### 1. Core Data Layer

#### [NEW] [Models.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/data/Models.kt)
```kotlin
@Serializable
data class GameState(
    val board: List<List<Cell>> = List(3) { List(3) { Cell.Empty } },
    val hostSymbol: Cell = Cell.X,
    val guestSymbol: Cell = Cell.O,
    val currentTurn: Cell = Cell.X, // Host starts
    val winner: Cell? = null,
    val isDraw: Boolean = false,
    val status: GameStatus = GameStatus.Waiting
)

enum class Cell { Empty, X, O }
enum class GameStatus { Waiting, Playing, Finished, OpponentDisconnected }
```

#### [NEW] [TicTacToeService.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/rpc/TicTacToeService.kt)
```kotlin
@LapisRpc("TicTacToeService")
interface TicTacToeService {
    @LapisMethod("requestGame")
    suspend fun requestGame(): Boolean

    @LapisMethod("gameState")
    fun gameState(): Flow<GameState>

    @LapisMethod("makeMove")
    suspend fun makeMove(@LapisParam("row") row: Int, @LapisParam("col") col: Int): Boolean
}
```

---

### 2. Device Discovery & Connection

#### [NEW] [DeviceDiscoveryViewModel.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/ui/discovery/DeviceDiscoveryViewModel.kt)
**State**:
- `scannedDevices: List<ScannedBluetoothDevice>`
- `pairedDevices: List<BluetoothDevice>`
- `isScanning: Boolean`
- `isBluetoothOn: Boolean`
- `pairingRequired: Boolean`
- `connectionStatus: ConnectionStatus (Disconnected, Connecting, Connected)`

**Actions**:
- `StartDiscovery`: Requests permissions -> Checks Bluetooth -> Starts `lapisBt.startScan()`.
- `TogglePairing(Boolean)`: Updates local preference.
- `Connect(BluetoothDevice)`: Calls `connectToDevice` or `connectToDeviceWithoutPairing`.
- `Disconnect(BluetoothDevice)`

---

### 3. Gameplay Logic

#### [NEW] [GameViewModel.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/ui/game/GameViewModel.kt)
**Role Logic**:
- If `Guest`: Obtains `TicTacToeService` proxy.
- If `Host`: Registers `TicTacToeService` implementation.

**State**:
- `board: List<List<Cell>>`
- `mySymbol: Cell`
- `isMyTurn: Boolean`
- `gameStatus: GameStatus`
- `pendingInvitation: String?` (Name of requester)

**Actions**:
- `AcceptInvitation` / `DeclineInvitation`
- `PlaceMove(row, col)`: Validates turn and calls `makeMove`.
- `RestartGame` / `LeaveGame`

---

### 4. UI Screens

#### [NEW] [DeviceDiscoveryScreen.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/ui/discovery/DeviceDiscoveryScreen.kt)
- Two sections: **Paired Devices** and **Discovered Devices**.
- Sticky header with "Pairing Required" toggle.
- Full-screen loading when connecting.

#### [NEW] [GameScreen.kt](file:///C:/Users/PC/Documents/@GitHub/BluetoothTicTacToe/app/src/main/java/com/elianfabian/bluetoothtictactoe/ui/game/GameScreen.kt)
- 3x3 Canvas or Grid for the board.
- Status bar (e.g., "Opponent's turn", "You won!").
- Dialog for receiving game invitations.

---

### 5. Technical Details & RPC Flow

- **RPC Flow**:
    - Both devices run a "Lobby" service or wait for a connection.
    - Once connected, the **Guest** calls `TicTacToeService.requestGame()`.
    - On the **Host** side, the implementation of `requestGame()` is a **suspending function** that:
        1. Updates its state to show an "Invitation Received" dialog.
        2. Waits for the user to press "Accept" or "Decline".
        3. Returns `true` or `false` to the Guest via RPC.
    - If `true`, both transition to the `Playing` status.
- **Error Handling**:
    - Use `lapisBt.events` to detect `OnDeviceDisconnected`.
    - Automatically end the game and show a notification if the peer leaves.

## Verification Plan

### Automated Tests
- Unit tests for `TicTacToeService` logic (win/draw detection).
- Mock `LapisBt` to verify `DeviceDiscoveryViewModel` state transitions.

### Manual Verification
1. Verify permission rationale appears if denied.
2. Verify "Pairing Required" toggle correctly switches between secure/insecure LapisBt calls.
3. Verify `ActivityResultBridge` correctly opens Bluetooth/Location settings.
4. Play games to verify RPC synchronization.
