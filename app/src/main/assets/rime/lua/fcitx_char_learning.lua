-- SPDX-License-Identifier: LGPL-2.1-or-later
-- SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
--
-- Count characters picked one at a time.
--
-- When a phrase is assembled from single characters (for example through the
-- keyboard's single-character filter), librime's script_translator learns the
-- new phrase but deliberately leaves the characters' own frequencies alone, so
-- a character picked this way never rises when typed by itself. This processor
-- records each such character as committed once as well.
--
-- The update is applied after librime has memorized the commit, inside the same
-- user dictionary transaction, so Backspace right after the commit still undoes
-- all of it. Schemas without script_translator are left untouched.

local M = {}

local function uses_script_translator(config)
  local list = config:get_list("engine/translators")
  if not list then return false end
  for i = 0, list.size - 1 do
    local item = list:get_value_at(i)
    local name = item and item.value or ""
    if name == "script_translator" or name == "r10n_translator" or
        name == "script_translator@translator" or name == "r10n_translator@translator" then
      return true
    end
  end
  return false
end

local function report(err)
  log.error("fcitx_char_learning: " .. tostring(err))
end

-- The updater's user dictionary commits the shared transaction when it is
-- destroyed, so it is only released right after a new transaction has started.
local function release_updater(env)
  if env.updater then
    env.updater = nil
    collectgarbage()
  end
end

local function apply_pending(env)
  local pending = env.pending
  if not pending then return end
  env.pending = nil
  local ok, err = pcall(function()
    -- a new Memory reads the user dictionary's current tick
    env.updater = env.updater or Memory(env.engine, env.engine.schema)
    for _, entry in ipairs(pending) do
      env.updater:update_userdict(entry, 1, "")
    end
  end)
  if not ok then report(err) end
end

function M.init(env)
  local ok, err = pcall(function()
    if not uses_script_translator(env.engine.schema.config) then return end
    env.memory = Memory(env.engine, env.engine.schema)
    env.memory:memorize(function(commit)
      release_updater(env)
      local recorded, failure = pcall(function()
        local elements = commit:get()
        if #elements < 2 then return end
        local copies = {}
        for i, element in ipairs(elements) do
          if utf8.len(element.text) ~= 1 then return end
          copies[i] = DictEntry(element)
        end
        env.pending = copies
      end)
      if not recorded then report(failure) end
      return true
    end)
    -- the context is cleared, and this fires, after every memory has handled the commit
    env.connection = env.engine.context.update_notifier:connect(function()
      apply_pending(env)
    end)
  end)
  if not ok then report(err) end
end

function M.func(key, env)
  return 2 -- kNoop
end

function M.fini(env)
  pcall(function()
    if env.connection then env.connection:disconnect() end
  end)
  env.connection = nil
  env.pending = nil
  env.memory = nil
  release_updater(env)
end

return M
