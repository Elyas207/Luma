package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaRadius

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.textCopyFromClipboard

@Composable
inline fun InputTextField(
    noinline onDismiss: () -> Unit,
    title: String,
    value: String,
    placeholder: String,
    crossinline setValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val txtFieldError = remember { mutableStateOf("") }
    val txtField = remember { mutableStateOf(value) }
    val value_cannot_empty = stringResource(R.string.value_cannot_be_empty)

    var copyText by remember { mutableStateOf(true) }
    if (copyText) {
        txtField.value = textCopyFromClipboard(context)
        copyText = false
    }

    txtField.value

    Column(
        modifier = modifier
            .padding(all = 10.dp)
            .background(color = LumaColor.Raised, shape = RoundedCornerShape( LumaRadius.Panel ))
            .padding(vertical = 16.dp)
            .defaultMinSize(Dp.Unspecified, 190.dp)
    ) {
        BasicText(
            text = title,
            style = LumaType.Tile,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 24.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {

            TextField(
                modifier = Modifier
                    //.padding(horizontal = 30.dp)
                    .fillMaxWidth(0.7f),
                colors = TextFieldDefaults.colors(
                    focusedPlaceholderColor = LumaColor.InkFaint,
                    unfocusedPlaceholderColor = LumaColor.InkFaint,
                    cursorColor = LumaColor.Ink,
                    focusedTextColor = LumaColor.Ink,
                    unfocusedTextColor = LumaColor.Ink,
                    focusedContainerColor = if (txtFieldError.value.isEmpty()) LumaColor.Raised else LumaColor.Alarm,
                    unfocusedContainerColor = if (txtFieldError.value.isEmpty()) LumaColor.Raised else LumaColor.Alarm,
                    focusedIndicatorColor = LumaColor.Ember,
                    unfocusedIndicatorColor = LumaColor.InkFaint
                ),
                leadingIcon = {
/*
                        Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.background0),
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp)
                                .clickable(
                                    indication = rememberRipple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    enabled = true,
                                    onClick = { onDismiss() }
                                )
                        )

 */

                },
                placeholder = { Text(text = placeholder) },
                value = txtField.value,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                onValueChange = {
                    txtField.value = it
                })
        }

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            DialogTextButton(
                text = stringResource( android.R.string.search_go ),
                onClick = {
                    if (txtField.value.isEmpty()) {
                        txtFieldError.value = value_cannot_empty
                    }
                    if (txtField.value.isNotEmpty()) {
                        setValue(txtField.value)
                    }
                }
            )

            DialogTextButton(
                text = stringResource( android.R.string.paste ),
                onClick = {
                    //txtField.value = ""
                    copyText = true
                },
                modifier = Modifier
            )

            DialogTextButton(
                text = stringResource(R.string.clear),
                onClick = { txtField.value = "" },
                modifier = Modifier
            )
        }

    }


}


