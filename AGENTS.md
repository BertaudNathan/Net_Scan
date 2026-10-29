# Project conventions

- Build Android interfaces with Kotlin, Jetpack Compose and Material 3. Do not use XML layouts for screens. Keep text in Android string resources and centralize colors, typography, spacing and reusable button styles.
- Respect the brand identity: navy `#0F056B`, green `#A7D7A9` and white. Ensure text contrast of at least 4.5:1, including in dark mode.
- Use a Compose NavHost, MVVM, Room for local persistence, domain-driven design and test-driven development. Represent loading, success, empty and error states when relevant.
- Follow Android accessibility practices, WCAG POUR and Nielsen's ten usability heuristics: screen-reader semantics, minimum 48dp touch targets, responsive layouts and system safe areas.
- Support system, light and dark themes and left-/right-handed use as the application evolves. Confirm before leaving, especially when changes could be lost.
- Keep prototypes visibly identifiable and do not imply that business operations, persistence or network measurements work before they are implemented.
