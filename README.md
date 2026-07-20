# Bluetooth Tic Tac Toe

Bluetooth Tic Tac Toe is an Android application designed to demonstrate the capabilities of the **LapisBt** library. It allows two players to play Tic Tac Toe over a Bluetooth connection using a modern, declarative approach for both UI (Jetpack Compose) and networking (RPC).

## Features

- **Device Discovery**: Scan for nearby Bluetooth devices and manage pairings.
- **Bluetooth RPC**: Utilizes the LapisBt library to synchronize game state and perform remote actions (making moves, restarting the game) via simple interface definitions.
- **Session Management**: Seamlessly handles Host and Guest roles.
- **Real-time Interaction**: Instant updates of game moves across both devices.
- **Modern Tech Stack**: Built with Kotlin, Jetpack Compose, Koin for Dependency Injection, and LapisBt for Bluetooth communication.

## LapisBt Library

This project serves as a practical example of how to integrate [LapisBt](https://github.com/ElianFabian/LapisBt). LapisBt is a library that simplifies Bluetooth communication in Android by providing an RPC-like (Remote Procedure Call) layer. Instead of manually handling Bluetooth sockets and byte streams, developers can define services as Kotlin interfaces and call methods remotely.

Key LapisBt features used in this app:
- `@LapisRpc` and `@LapisMethod` annotations for defining the game service.
- Automatic serialization of data classes (GameState, PlayerState) over Bluetooth.
- Flow-based state observation across the connection.

## Screenshots

### Discovery Screen
*The discovery screen allows you to scan for nearby devices, manage connections, and send/receive game invitations.*

<p align="center">
  <img src="https://github.com/user-attachments/assets/26424070-7e6d-4477-ae4a-9bf5a444735d" alt="Discovery Screen" width="300">
</p>

### Game Screen
*The game screen features a classic Tic Tac Toe board where moves are synchronized in real-time between the two connected devices.*

<p align="center">
  <img src="https://github.com/user-attachments/assets/3cb47092-0b1f-47bb-869a-223f70252400" alt="Game Screen" width="300">
</p>

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Ensure Bluetooth is enabled on two Android devices.
4. Build and install the app on both devices.
5. Use the Discovery Screen to find and connect to the other device.
6. Send an invitation and start playing!

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
