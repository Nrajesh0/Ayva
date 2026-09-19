/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util

object FocusQuotes {
    val DEFAULT_QUOTES = listOf(
        "Look at you, looking for dopamine in all the wrong places.",
        "Spoiler alert: whatever you're avoiding will still be there when you finish scrolling.",
        "Put the phone down. The algorithm doesn't love you back.",
        "Your thumb needs to calm down. It's doing too much.",
        "Don't make me tell your future self about this.",
        "Are you actually bored, or are you just scared of your own thoughts?",
        "Breaking news: nothing exciting happened on this app in the last 4 minutes.",
        "Go drink some water. You’re just thirsty, not curious.",
        "Even your battery percentage is judging you right now.",
        "Close the app. The memes will survive without you.",
        "You really tapped this out of pure muscle memory, didn't you?",
        "If scrolling burned calories, you'd be an Olympic athlete by now.",
        "Nice try, but today is not the day we ruin your productivity.",
        "Back away slowly and pretend you didn't just click this.",
        "Your weekly screen time report is already filing a formal complaint.",
        "Remember your goals? Neither do you, apparently.",
        "There are zero millionaires who got rich from refreshing this feed.",
        "What are you doing here? Go be mysterious and productive.",
        "You are exactly one tap away from a 45-minute black hole. Abort mission!",
        "Go stare at a wall instead. It builds character.",
        "Is this app paying your rent? Didn't think so.",
        "Blink twice if this app is holding your attention hostage.",
        "You have tasks to do, emails to send, or a life to live. Pick one.",
        "Error 404: Willpower not found. Deploying emergency delay screen...",
        "Your FBI agent is tired of watching you cycle through the same three apps.",
        "Do you really want to watch a 10-part video about a stranger's drama right now?",
        "Step away from the glowing rectangle. Go touch some grass.",
        "That task you're running from isn't going to complete itself.",
        "You didn't survive the entire day just to get defeated by a little app icon.",
        "Close the screen and go pretend to be a functioning adult."
    )

    fun getQuoteOrDefault(customQuote: String?): String {
        val trimmed = customQuote?.trim()
        if (!trimmed.isNullOrBlank() && 
            trimmed != "Is this urgent, or are you chasing cheap dopamine?" && 
            trimmed != "Are you chasing cheap dopamine?") {
            return trimmed
        }
        return DEFAULT_QUOTES.random()
    }
}
