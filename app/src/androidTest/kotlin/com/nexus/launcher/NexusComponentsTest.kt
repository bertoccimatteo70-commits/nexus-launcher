package com.nexus.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusTheme
import com.nexus.core.designsystem.NexusThemeConfig
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusSliderRow
import com.nexus.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexusComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryActionIsClickableAndVisible() {
        var clicked = false
        compose.setContent {
            NexusTheme(NexusThemeConfig(darkTheme = true)) {
                NexusButton("Avvia Nexus", { clicked = true })
            }
        }
        compose.onNodeWithText("Avvia Nexus").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun themeChoiceUpdatesState() {
        compose.setContent {
            NexusTheme(NexusThemeConfig(darkTheme = true)) {
                var selected by remember { mutableStateOf(ThemeMode.SYSTEM) }
                Column {
                    NexusChoiceRow(
                        options = ThemeMode.entries,
                        selected = selected,
                        label = { it.label },
                        onSelect = { selected = it },
                    )
                    Text("Tema attuale: ${selected.label}")
                }
            }
        }
        compose.onNodeWithText("Scuro").performClick()
        compose.onNodeWithText("Tema attuale: Scuro").assertIsDisplayed()
        assertEquals(ThemeMode.DARK.label, "Scuro")
    }

    @Test
    fun iconButtonExposesAccessibleLabel() {
        var clicked = false
        compose.setContent {
            NexusTheme(NexusThemeConfig(darkTheme = true)) {
                NexusIconButton(NexusGlyphId.SEARCH, "Cerca nel dispositivo", { clicked = true })
            }
        }
        compose.onNodeWithContentDescription("Cerca nel dispositivo").performClick()
        assertTrue(clicked)
    }

    @Test
    fun sliderValueLabelIsReadable() {
        compose.setContent {
            NexusTheme(NexusThemeConfig(darkTheme = true)) {
                NexusSliderRow(
                    title = "Dimensione caratteri",
                    valueLabel = "120%",
                    value = 1.2f,
                    range = 0.85f..1.6f,
                    onValueChange = {},
                )
            }
        }
        compose.onNodeWithText("120%").assertIsDisplayed()
    }
}
