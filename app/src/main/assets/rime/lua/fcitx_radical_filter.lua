-- Attach an invisible radical marker to single-character candidates.
-- The radical comes from the current schema's configured Rime reverse-lookup
-- dictionary; the Android UI removes this marker before drawing comments.

local marker_start = utf8.char(0x2063) .. "fcitx-radical:"
local marker_end = utf8.char(0x2063)

local function exact_component(env, code)
  local cached = env.component_cache[code]
  if cached ~= nil then
    return cached ~= false and cached or nil
  end
  env.memory:dict_lookup(code, false, 64)
  for entry in env.memory:iter_dict() do
    if utf8.len(entry.text) == 1 then
      local entry_codes = env.reverse:lookup(entry.text)
      if entry_codes and entry_codes:match("^([^%s]+)") == code then
        env.component_cache[code] = entry.text
        return entry.text
      end
    end
  end
  env.component_cache[code] = false
end

local function radical_for(env, text)
  local cached = env.cache[text]
  if cached ~= nil then
    return cached ~= false and cached or nil
  end

  local codes = env.reverse:lookup(text)
  local first_code = codes and codes:match("^([^%s]+)")
  local radical = first_code and exact_component(env, first_code) or nil
  env.cache[text] = radical or false
  return radical
end

local function init(env)
  env.cache = {}
  env.component_cache = {}
  local config = env.engine.schema.config
  local dictionary = config:get_string("radical_reverse_lookup/dictionary")
  if not dictionary or dictionary == "" then
    -- Most maintained radical/decomposition schemes expose this Rime
    -- dictionary even when the active schema does not repeat the setting.
    dictionary = "radical_pinyin"
  end

  local reverse_ok, reverse = pcall(ReverseLookup, dictionary)
  local memory_ok, memory = pcall(function()
    return Memory(env.engine, Schema(dictionary))
  end)
  if reverse_ok and memory_ok and reverse and memory then
    env.reverse = reverse
    env.memory = memory
  end
end

local function filter(input, env)
  for candidate in input:iter() do
    if env.reverse and env.memory and utf8.len(candidate.text) == 1 then
      local radical = radical_for(env, candidate.text)
      if radical then
        candidate:get_genuine().comment = (candidate.comment or "")
          .. marker_start .. radical .. marker_end
      end
    end
    yield(candidate)
  end
end

local function fini(env)
  env.cache = nil
  env.component_cache = nil
  env.memory = nil
  env.reverse = nil
end

return { init = init, func = filter, fini = fini }
