-- Deletes the current line and its newline (like 'dd').
vim.register("dl", function()
  local t = vim.get_text()
  local s = vim.get_sel()
  local ls = #(t:sub(1, s):match("^.*\n") or "")
  local le = t:find("\n", s + 1, true) or #t
  vim.replace(ls, le, "")
end)