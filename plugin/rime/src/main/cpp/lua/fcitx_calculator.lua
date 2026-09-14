-- Calculator translator for the Rime instance embedded in Fcitx5 for Android.
-- Type an expression beginning with '='. The result is offered as a candidate.

local M = {}

local function factorial(value)
    assert(value >= 0 and value <= 170 and value == math.floor(value), "factorial expects an integer from 0 to 170")
    local result = 1
    for i = 2, value do result = result * i end
    return result
end

local methods = {
    abs = math.abs,
    acos = math.acos,
    asin = math.asin,
    atan = math.atan,
    ceil = math.ceil,
    cos = math.cos,
    deg = math.deg,
    e = math.exp(1),
    exp = math.exp,
    fact = factorial,
    floor = math.floor,
    log = math.log,
    max = math.max,
    min = math.min,
    pi = math.pi,
    rad = math.rad,
    sin = math.sin,
    sqrt = math.sqrt,
    tan = math.tan,
}

local function normalize(expression)
    local result = expression:gsub("×", "*")
    result = result:gsub("÷", "/")
    result = result:gsub("−", "-")
    result = result:gsub("π", "pi")
    result = result:gsub("（", "(")
    result = result:gsub("）", ")")
    return result:gsub("([0-9]+)!", "fact(%1)")
end

local function evaluate(expression)
    if #expression == 0 or #expression > 160 then return nil, "算式为空或过长" end
    local code = normalize(expression)
    if not code:match("^[%w_%s+*/^%%(),.%-]+$") then return nil, "包含不支持的字符" end
    for name in code:gmatch("[%a_][%w_]*") do
        if methods[name] == nil then return nil, "未知函数：" .. name end
    end
    local chunk, parse_error = load("return (" .. code .. ")", "calculator", "t", methods)
    if not chunk then return nil, parse_error end
    local ok, value = pcall(chunk)
    if not ok then return nil, tostring(value) end
    if type(value) ~= "number" or value ~= value or value == math.huge or value == -math.huge then
        return nil, "结果不是有限数字"
    end
    if value == math.floor(value) then return string.format("%.0f", value) end
    return string.format("%.12g", value)
end

function M.func(input, segment, env)
    if input:sub(1, 1) ~= "=" then return end
    local expression = input:sub(2)
    if expression == "" then return end
    local result, message = evaluate(expression)
    if result then
        yield(Candidate("calculator", segment.start, segment._end, result, "〔计算器〕"))
        yield(Candidate("calculator", segment.start, segment._end, expression .. "=" .. result, "算式与结果"))
    else
        yield(Candidate("calculator", segment.start, segment._end, expression, "计算器 · " .. message))
    end
end

return M
