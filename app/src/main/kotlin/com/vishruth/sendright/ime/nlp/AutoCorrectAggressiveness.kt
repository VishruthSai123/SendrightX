/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.key1.ime.nlp

/**
 * Enum class defining the aggressiveness level for auto-correction.
 * This determines how readily the system will automatically correct typed words.
 */
enum class AutoCorrectAggressiveness {
    /**
     * Conservative auto-correction - only corrects very obvious typos with high confidence.
     * Requires 95%+ confidence and exact or single-character corrections.
     */
    CONSERVATIVE,
    
    /**
     * Moderate auto-correction - balanced approach with good accuracy.
     * Corrects common typos and obvious mistakes with 75%+ confidence.
     */
    MODERATE,
    
    /**
     * Aggressive auto-correction - more liberal correction approach.
     * Corrects various typos and suggests completions with 60%+ confidence.
     */
    AGGRESSIVE,
}