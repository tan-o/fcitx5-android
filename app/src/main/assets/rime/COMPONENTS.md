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

## Character learning

lua/fcitx_char_learning.lua (LGPL-2.1-or-later, this project) is added to every
schema's processors. When a phrase is assembled from single characters picked one
at a time, librime's script_translator learns only the phrase; the processor also
counts each picked character once. It writes inside librime's open user dictionary
transaction, so Backspace right after the commit still undoes the learning.
CI checks this with real librime and Mint by exporting the user dictionary.
