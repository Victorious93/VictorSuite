---
title: Colors and fonts | VictorSuite
---

# Colors and fonts

VictorSuite styles the terminal with files in `~/.termux/`, the same way Termux does. You do not need a separate styling app.

## In the app

- **Font settings** (side menu, under Beautification/UI Functions) lets you pick a font. VictorSuite copies the chosen file to `~/.termux/font.ttf`.
- **Beautification Settings**, snowflake, particle animation, and **Clear Beautification** in the same menu change the app chrome (backgrounds and effects), not the terminal colour theme.

## Colour theme

To change terminal colours, edit `~/.termux/colors.properties`, then reload the style (for example by long-pressing the terminal and choosing **Style**, or by restarting the app). A typical file looks like:

```
foreground=#c8d3f5
background=#1b1d2b
cursor=#c8d3f5
color0=#1b1d2b
color1=#ff757f
# ... color2–color15
```

Put the file at `~/.termux/colors.properties`. Invalid colour values are ignored.

## Style menu item

Long-press the terminal and choose **Style**. If the optional Termux:Styling plugin is installed, VictorSuite still opens it. If it is not, this page opens instead of an F-Droid download. Built-in font settings and `colors.properties` are enough for most colour and font changes.

See also the [manual](MANUAL.md).
