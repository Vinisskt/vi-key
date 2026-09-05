-- Reverses the selected text (or complains if nothing is selected).
vim.register("rev", function()
  local s, e = vim.get_sel()
  if e <= s then
    vim.status("select some text first")
    return
  end
  local text = vim.get_text()
  vim.replace(s, e, text:sub(s + 1, e):reverse())
end)