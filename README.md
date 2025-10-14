<img align="left" width="80" height="80"
src=".github/repo_icon.png" alt="App icon">

# SendRight - AI-Enhanced Keyboard

> **Note**: This repository contains SendRight, an AI-enhanced Android keyboard based on the original FlorisBoard project by Patrick Goldinger and The FlorisBoard Contributors. The original FlorisBoard project can be found at: https://github.com/florisboard/florisboard

[![Crowdin](https://badges.crowdin.net/florisboard/localized.svg)](https://crowdin.florisboard.org) [![Matrix badge](https://img.shields.io/badge/chat-%23florisboard%3amatrix.org-blue)](https://matrix.to/#/#florisboard:matrix.org) [![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.1-4baaaa.svg)](CODE_OF_CONDUCT.md)

**SendRight 4.1** combines FlorisBoard's solid foundation with cutting-edge AI features. Experience intelligent typing with Magic Wand text processing, custom AI assistants, intelligent workspaces, seamless Gemini API integration, and advanced personalization.

## Attribution

This project is based on [FlorisBoard](https://github.com/florisboard/florisboard) by Patrick Zedler and The FlorisBoard Contributors, licensed under the Apache License 2.0.

<table>
<tr>
<th align="center" width="50%">
<h3>Stable <a href="https://github.com/florisboard/florisboard/releases/latest"><img alt="Latest stable release" src="https://img.shields.io/github/v/release/florisboard/florisboard?sort=semver&display_name=tag&color=28a745"></a></h3>
</th>
<th align="center" width="50%">
<h3>Preview <a href="https://github.com/florisboard/florisboard/releases"><img alt="Latest preview release" src="https://img.shields.io/github/v/release/florisboard/florisboard?include_prereleases&sort=semver&display_name=tag&color=fd7e14"></a></h3>
</th>
</tr>
<tr>
<td valign="top">
<p><i>Major versions only</i><br><br>Updates are more polished, new features are matured and tested through to ensure a stable experience.</p>
</td>
<td valign="top">
<p><i>Major + Alpha/Beta/Rc versions</i><br><br>Updates contain new features that may not be fully matured yet and bugs are more likely to occur. Allows you to give early feedback.</p>
</td>
</tr>
<tr>
<td valign="top">
<p><a href="https://f-droid.org/packages/dev.patrickgold.florisboard"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="64" alt="F-Droid badge"></a></p>
<p>

**Google Play**: Join the [FlorisBoard Test Group](https://groups.google.com/g/florisboard-closed-beta-test), then visit the [testing page](https://play.google.com/apps/testing/dev.patrickgold.florisboard). Once joined and installed, updates will be delivered like for any other app. ([Store entry](https://play.google.com/store/apps/details?id=dev.patrickgold.florisboard))

</p>
<p>

**Obtainium**: [Auto-import stable config][obtainium_stable]

</p>
<p>

**Manual**: Download and install the APK from the release page.

</p>
</td>
<td valign="top">
<p><a href="https://apt.izzysoft.de/fdroid/index/apk/dev.patrickgold.florisboard.beta"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="64" alt="IzzySoft repo badge"></a></p>
<p>

**Google Play**: Join the [FlorisBoard Test Group](https://groups.google.com/g/florisboard-closed-beta-test), then visit the [preview testing page](https://play.google.com/apps/testing/dev.patrickgold.florisboard.beta). Once joined and installed, updates will be delivered like for any other app. ([Store entry](https://play.google.com/store/apps/details?id=dev.patrickgold.florisboard.beta))

</p>
<p>

**Obtainium**: [Auto-import preview config][obtainium_preview]

</p>
<p>

**Manual**: Download and install the APK from the release page.

</p>
</td>
</tr>
</table>

Beginning with v0.7 FlorisBoard will enter the public beta on Google Play.

## Highlighted Features

### 🪄 AI-Powered Magic Wand
- **Study Tools**: Explain concepts, format equations, step-by-step solutions
- **Writing Enhancement**: Rewrite, summarize, optimize, and formalize text
- **Tone Control**: Switch between casual, professional, friendly, and creative styles
- **Translation Hub**: Multi-language support with intelligent context awareness
- **Chat Integration**: Direct conversational AI for real-time assistance

### 🤖 Custom AI Assistants & AI Workspace
- **Custom AI Actions**: Create personalized AI commands with custom prompts
- **Popular Actions**: Pre-built actions like "Humanise", "GenZ Translate", "Reply"
- **Dynamic Workspace**: Context-aware AI suggestions that adapt to your usage
- **Personal Context**: AI can learn your personal details and preferences
- **Pro Features**: Advanced AI capabilities with subscription support

### 🎨 Advanced Customization & Theming
- **Snygg Theming System**: Material You integration with comprehensive customization
- **Smart Clipboard**: Intelligent history management with organization features
- **Glide Typing**: Precision gesture recognition with visual feedback trails
- **Voice Integration**: Seamless Google Voice Typing handoff
- **Emoji Intelligence**: Complete Unicode support with smart suggestions

### 🌐 Core Keyboard Features
- **50+ Languages**: Multi-language support with RTL script compatibility
- **Gesture Support**: Customizable swipe actions and precision controls
- **Extension Support**: Evolving ecosystem for themes and functionality
- **Privacy-First**: Local processing with encrypted AI communications

> [!IMPORTANT]
> SendRight includes advanced AI features powered by Gemini API integration.
> AI usage tracking and Pro subscription features are available for enhanced functionality.

## 🔒 Privacy & Security
- **Zero Data Collection**: Complete privacy protection with local processing priority
- **Encrypted AI Communications**: Secure API interactions with fallback systems
- **Transparent Permissions**: Clear control over data access and AI features
- **Open Source Foundation**: Built on trusted FlorisBoard architecture with enhancements

## 🎯 Perfect For
- **Professionals**: AI-assisted business communication and multilingual correspondence
- **Students**: Academic writing with equation support and research assistance
- **Content Creators**: Smart social media optimization and creative writing tools
- **Power Users**: Custom AI integration, advanced workspace features, and pro capabilities

## Contributing
Want to contribute to SendRight? We welcome contributions! Please see the [contribution guidelines](CONTRIBUTING.md) for more info. This project maintains compatibility with FlorisBoard's contribution standards while adding AI-specific enhancements.

## Addons Store
The official [FlorisBoard Addons Store](https://beta.addons.florisboard.org) remains compatible with SendRight for themes and extensions.

> [!NOTE]
> SendRight extends FlorisBoard with AI features while maintaining full compatibility with existing FlorisBoard themes and extensions.

## List of Permissions SendRight Requests
Please refer to this [page](https://github.com/florisboard/florisboard/wiki/List-of-permissions-FlorisBoard-requests) for detailed information about permissions. SendRight includes additional AI-related permissions for enhanced functionality including internet access for Gemini API integration.

## APK signing certificate hashes

The package names and SHA-256 hashes of the signature certificate are listed below, so you can verify both FlorisBoard variants with apksigner by using `apksigner verify --print-certs florisboard-<version>-<track>.apk` when you download the APK.
If you have [AppVerifier](https://github.com/soupslurpr/AppVerifier) installed, you can alternatively copy both the package name and the hash of the corresponding track and share them to AppVerifier.

##### Stable track:

dev.patrickgold.florisboard<br>
0B:80:71:64:50:8E:AF:EB:1F:BB:81:5B:E7:A2:3C:77:FE:68:9D:94:B1:43:75:C9:9B:DA:A9:B6:57:7F:D6:D6

##### Preview track:

dev.patrickgold.florisboard.beta<br>
0B:80:71:64:50:8E:AF:EB:1F:BB:81:5B:E7:A2:3C:77:FE:68:9D:94:B1:43:75:C9:9B:DA:A9:B6:57:7F:D6:D6


## Used Libraries and Components
### Core FlorisBoard Libraries
* [AndroidX libraries](https://github.com/androidx/androidx) by [Android Jetpack](https://github.com/androidx)
* [AboutLibraries](https://github.com/mikepenz/AboutLibraries) by [mikepenz](https://github.com/mikepenz)
* [Google Material icons](https://github.com/google/material-design-icons) by [Google](https://github.com/google)
* [JetPref preference library](https://github.com/patrickgold/jetpref) by [patrickgold](https://github.com/patrickgold)
* [KotlinX coroutines library](https://github.com/Kotlin/kotlinx.coroutines) by [Kotlin](https://github.com/Kotlin)
* [KotlinX serialization library](https://github.com/Kotlin/kotlinx.serialization) by [Kotlin](https://github.com/Kotlin)

### SendRight AI Enhancements
* **Google Gemini API** - Advanced AI text processing and enhancement
* **Custom AI Framework** - Personalized assistant and workspace intelligence
* **Context Management System** - Personal details and custom variable handling
* **Usage Tracking & Analytics** - AI feature usage monitoring and optimization
* **Subscription Management** - Pro feature access and billing integration

Many thanks to [Nikolay Anzarov](https://www.behance.net/nikolayanzarov) ([@BloodRaven0](https://github.com/BloodRaven0)) for designing the original FlorisBoard icon foundation!

## License
```
Copyright 2020-2025 The FlorisBoard Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

**SendRight**: Where Intelligence Meets Artistry  
*Enhanced AI Features • Built on FlorisBoard Excellence*

Crafted with 💚 by **Vishruth Technologies**  
Special thanks to Patrick Goldinger and The FlorisBoard Contributors for the exceptional foundation.

<!-- BEGIN SECTION: obtainium_links -->
<!-- auto-generated link templates, do NOT edit by hand -->
<!-- see fastlane/update-readme.sh -->
[obtainium_preview]: https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22dev.patrickgold.florisboard.beta%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fflorisboard%2Fflorisboard%22%2C%22author%22%3A%22florisboard%22%2C%22name%22%3A%22FlorisBoard%20Preview%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22preview%5C%22%7D%22%7D%0A
[obtainium_stable]: https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22dev.patrickgold.florisboard%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fflorisboard%2Fflorisboard%22%2C%22author%22%3A%22florisboard%22%2C%22name%22%3A%22FlorisBoard%20Stable%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22stable%5C%22%7D%22%7D%0A
<!-- END SECTION: obtainium_links -->
