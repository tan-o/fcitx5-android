# Rime candidate components

Uses the unmodified moqi_chaifen_all.txt and moqi_chaifen_all.json from
https://github.com/gaboolic/rime-frost/tree/77d2026c812c6939e0cf47637175a0122965c70f/opencc
under the upstream GPL-3.0 license (COMPONENTS-LICENSE).

Rime's native simplifier performs indexed OpenCC lookups. No generated reverse
dictionary, application-side dictionary scanning or decomposition Lua remains.
The comment formatter wraps returned components in candidate metadata; candidate
text and inherited comments are preserved. The UI splits the result into chips.

Installation runs before native engine startup and explicit deployment, using
custom YAML patches. Source schemes are not rewritten. Installation errors are
reported in Rime settings without taking down the IME.

CI uses the actual Kotlin installer with a pinned complete upstream Mint scheme,
then checks 咁/呀/中/只/找/河 and traditional 語 via librime's global candidate API
and verifies selected text commits unchanged. The APK gate checks the assets.
Coverage and decomposition conventions follow Moqi, not a dictionary-standard
single Kangxi radical for every Unicode character. Device UI validation is separate.
