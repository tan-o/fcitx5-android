# Component data
Source: https://github.com/mirtlecn/rime-radical-pinyin/blob/87e73d7182916f3db9dbc0b6a4d18a78a775b298/src/dict/radical.yaml
Copyright: upstream contributors (see upstream history). License: GPL-3.0.
fcitx_components.dict.yaml preserves every upstream decomposition. Whitespace
between glyphs is removed so each decomposition is one Rime code; duplicate rows
are removed. No phonetic guessing or character-shape inference is performed.
The user's input schema remains unchanged except for a dependency and Lua filter.
Rime compiles the dictionary and ReverseLookup returns the glyph codes; the UI
offers all returned components, not a guessed official Kangxi radical.
The internal dependency schema is not added to schema_list.

