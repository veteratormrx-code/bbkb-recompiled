package dev.bbkb.ime.core.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bbkb.ime.R
import dev.bbkb.ime.core.settings.ui.Custom
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.mlkit.MlKitModels

/**
 * Model downloads only. Recognition and translation happen on the keyboard, not on this page.
 */
@Composable
fun MlKitToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val status = remember { MlKitModels.gmsStatus(context) }
    val ready = stringResource(R.string.mlkit_model_ready)
    val failed = stringResource(R.string.mlkit_download_failed)
    SettingsScreenHost(
        R.string.settings_mlkit_title,
        onNavigateBack,
        listOf(
            Custom {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            if (status.gmsAvailable) R.string.mlkit_gms_ready else R.string.mlkit_gms_missing
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ModelButton(R.string.mlkit_download_en_ru, status.gmsAvailable) {
                        MlKitModels.downloadTranslate(MlKitModels.english(), MlKitModels.russian()) { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    ModelButton(R.string.mlkit_download_ru_en, status.gmsAvailable) {
                        MlKitModels.downloadTranslate(MlKitModels.russian(), MlKitModels.english()) { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    ModelButton(R.string.mlkit_delete_ru_translate, status.gmsAvailable) {
                        MlKitModels.deleteTranslate(MlKitModels.russian()) { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    ModelButton(R.string.mlkit_download_ink_ru, status.gmsAvailable) {
                        MlKitModels.downloadInk("ru") { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    ModelButton(R.string.mlkit_download_ink_en, status.gmsAvailable) {
                        MlKitModels.downloadInk("en") { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    ModelButton(R.string.mlkit_delete_ink_ru, status.gmsAvailable) {
                        MlKitModels.deleteInk("ru") { ok, _ ->
                            Toast.makeText(context, if (ok) ready else failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        ),
    )
}

@Composable
private fun ModelButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(stringResource(label))
    }
}
