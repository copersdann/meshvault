package com.meshvault.app.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.meshvault.app.ui.theme.MeshVaultTheme
import org.junit.Rule
import org.junit.Test

/** Дымовой UI-тест темы и рендера Compose. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun themeRendersRussianText() {
        composeTestRule.setContent {
            MeshVaultTheme(themeMode = "dark", accent = "green") {
                MaterialTheme {
                    Text("Библиотека моделей")
                }
            }
        }
        composeTestRule.onNodeWithText("Библиотека моделей").assertExists()
    }
}
