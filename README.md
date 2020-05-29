# AnvilGUI
> Keyword: `anvil`
### Info
* This is a special menu type only for getting inputs.
* In this menu type, the addon only apply ONE icon.
* The variable `{anvil_input}` stores the user's input.
### Format
```yaml
# Settings
menu-settings:
  menu-type: anvil
  <setting1>
  <setting2>
  ...

# Icon
icon:
  ...
```
### Available Settings
* `title`: the title of the menu
* `text`: the text to show when the player opens the menu
* `complete-action`: the commands executed when the player clicks the result slot (the last slot)
* `close-action`: the commands executed when closing the menu
* `prevent-close`: whether the addon prevents the player from closing the menu
* `command`: the commands to open the menu
* `clear-input-on-complete`: whether the addon will clear the user's input when completed
### Example
```yaml
menu-settings:
  menu-type: anvil
  command: testanvil
  title: "&cTest Anvil"
  text: "What is your name?"
  clear-input-on-complete: true
  complete-action:
  - "tell: &aHello, {anvil_input}"
  prevent-close: true
  close-action:
  - "tell: &cYou closed the menu"
  
icon:
  id: paper
```