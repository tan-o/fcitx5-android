-- Upstream component glyphs, returned directly by Rime.
local marker = utf8.char(0x2063)
local function init(env)
  env.reverse = ReverseLookup("fcitx_components")
  assert(env.reverse and env.reverse:lookup("呀"):find("口", 1, true),
    "fcitx_components reverse dictionary was not deployed")
end
local function filter(input, env)
  for candidate in input:iter() do
    if utf8.len(candidate.text) == 1 then
      local seen, components = {}, {}
      for _, cp in utf8.codes(env.reverse:lookup(candidate.text)) do
        local glyph = utf8.char(cp)
        if glyph ~= " " and not seen[glyph] then
          seen[glyph] = true
          components[#components + 1] = glyph
        end
      end
      if #components > 0 then
        -- Shadow the displayed candidate: wrappers can own their comments.
        candidate = ShadowCandidate(candidate, candidate.type, candidate.text,
          (candidate.comment or "") .. marker .. "fcitx-radical:"
          .. table.concat(components) .. marker)
      end
    end
    yield(candidate)
  end
end
return { init = init, func = filter }
