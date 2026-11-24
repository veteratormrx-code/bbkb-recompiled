package dev.bbkb.ime.core.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bbkb.ime.R
import dev.bbkb.ime.core.settings.ui.Custom
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.mlkit.HandwritingView
import dev.bbkb.ime.mlkit.MlKitModels

@Composable
fun MlKitToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val status = remember { MlKitModels.gmsStatus(context) }
    SettingsScreenHost(
        R.string.settings_mlkit_title,
        onNavigateBack,
        listOf(
            Custom {
                Column(Modifier.padding(16.dp)) {
                    Text(status.message)
                    Button(
                        onClick = {
                            MlKitModels.downloadTranslate(MlKitModels.english(), MlKitModels.russian()) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Download EN→RU") }
                    Button(
                        onClick = {
                            MlKitModels.downloadTranslate(MlKitModels.russian(), MlKitModels.english()) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Download RU→EN") }
                    Button(
                        onClick = {
                            MlKitModels.deleteTranslate(MlKitModels.russian()) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Delete Russian translate model") }
                    Button(
                        onClick = {
                            MlKitModels.downloadInk("ru") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Download Russian handwriting") }
                    Button(
                        onClick = {
                            MlKitModels.downloadInk("en") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Download English handwriting") }
                    Button(
                        onClick = {
                            MlKitModels.deleteInk("ru") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Delete Russian handwriting") }
                    val pad = remember { HandwritingView(context) }
                    AndroidView(
                        factory = { pad },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Button(
                        onClick = {
                            MlKitModels.recognizeInk("ru", pad.currentInk()) { _, msg ->
                                Toast.makeText(context, msg.ifEmpty { "No text" }, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = status.gmsAvailable,
                    ) { Text("Recognize") }
                    Button(onClick = { pad.clear() }) { Text("Clear pad") }
                }
            },
        ),
    )
}
