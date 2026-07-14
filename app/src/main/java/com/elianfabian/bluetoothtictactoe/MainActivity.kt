package com.elianfabian.bluetoothtictactoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.elianfabian.bluetoothtictactoe.ui.theme.BluetoothTicTacToeTheme
import com.elianfabian.lapisbt.LapisBt

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			LapisBt.newInstance(this)

			BluetoothTicTacToeTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					Greeting(
						name = "Android",
						modifier = Modifier.padding(innerPadding)
					)
				}
			}
		}
	}
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
	Text(
		text = "Hello $name!",
		modifier = modifier
	)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
	BluetoothTicTacToeTheme {
		Greeting("Android")
	}

}

val CurrentNativeType = Nothing::class

abstract class NativeArray<T> {

	constructor(size: Int, init: (index: Int) -> T)

	abstract operator fun get(index: Int): Int

	abstract operator fun set(index: Int, value: Int)

	abstract val size: Int

	abstract operator fun iterator(): Iterator<T>
}
