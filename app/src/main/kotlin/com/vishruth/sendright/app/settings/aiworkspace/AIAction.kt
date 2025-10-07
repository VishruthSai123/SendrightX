/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Represents an AI action that can be executed from the MagicWand panel
 */
@Serializable
data class AIAction(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String,
    val iconName: String = "auto_awesome",
    val isPopular: Boolean = false,
    val isUserCreated: Boolean = false,
    val isEnabled: Boolean = true,
    val includePersonalDetails: Boolean = false,
    val includeDateTime: Boolean = false
) {
    /**
     * Get the appropriate icon for this AI action
     */
    fun getIcon(): ImageVector {
        return when (iconName) {
            "person" -> Icons.Default.Person
            "trending_up" -> Icons.Default.TrendingUp
            "chat_bubble" -> Icons.Default.ChatBubble
            "edit" -> Icons.Default.Edit
            "facebook" -> Icons.Default.Face
            "camera_alt" -> Icons.Default.CameraAlt
            "emoji_emotions" -> Icons.Default.EmojiEmotions
            "play_circle" -> Icons.Default.PlayCircle
            "auto_awesome" -> Icons.Default.AutoAwesome
            else -> Icons.Default.AutoAwesome
        }
    }
}

/**
 * Predefined popular AI actions
 */
object PopularAIActions {
    val actions = listOf(
        AIAction(
            id = "humanise",
            title = "Humanise",
            description = "Speak like a human",
            prompt = "Rewrite the following text to sound more natural, human-like, and conversational while maintaining the original meaning and key information:",
            iconName = "person",
            isPopular = true
        ),
        AIAction(
            id = "reply",
            title = "Reply",
            description = "One Single Response to a message",
            prompt = "Generate a single, appropriate response to the following message. Keep it concise and contextually relevant:",
            iconName = "chat_bubble",
            isPopular = true
        ),
        AIAction(
            id = "genz_translate",
            title = "GenZ",
            description = "Translate to Gen Z slang and style",
            prompt = "rewrite this text in a way that sounds more natural and relatable, Use some casual Gen Z expressions and vibes, but keep it readable and not too over-the-top. Just make it sound like how someone our age would actually say it",
            iconName = "trending_up",
            isPopular = true
        ),
        AIAction(
            id = "continue_writing",
            title = "Continue Writing",
            description = "Provide the starting of a sentence to autocomplete",
            prompt = "Continue writing from where this text left off, maintaining the same tone, style, and context:",
            iconName = "edit",
            isPopular = true
        ),
        AIAction(
            id = "instagram_caption",
            title = "Instagram Caption",
            description = "Create an Instagram Caption",
            prompt = "Create an engaging Instagram caption with relevant hashtags based on the following content:",
            iconName = "camera_alt",
            isPopular = true
        ),
        AIAction(
            id = "phrase_to_emoji",
            title = "Phrase to Emoji",
            description = "Convert text phrases to emojis",
            prompt = "Convert the following text into appropriate emojis that represent the meaning and emotion:",
            iconName = "emoji_emotions",
            isPopular = true
        ),
        AIAction(
            id = "youtube_description",
            title = "Youtube Description",
            description = "Create a description for Youtube video",
            prompt = "Create a compelling YouTube video description based on the following content. Include relevant keywords and call-to-action:",
            iconName = "play_circle",
            isPopular = true
        ),
        AIAction(
            id = "facebook_post",
            title = "Facebook Post",
            description = "Create a Caption for your Facebook post",
            prompt = "Create an engaging Facebook post caption based on the following content. Make it social media friendly with appropriate tone:",
            iconName = "facebook",
            isPopular = true
        )
    )
}