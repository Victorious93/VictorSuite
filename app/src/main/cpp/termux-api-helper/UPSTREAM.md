# termux-api helper (vendored, MIT)

Source: https://github.com/termux/termux-api-package, commit `9e7f1531e1aa4a9c1e261a2dbde4de93653c366e`
(`termux-api.c`, `termux-api-broadcast.c`, `termux-api.h`, `LICENSE`). MIT license, kept as `LICENSE`.

This is the program the `termux-*` shell commands run (`$PREFIX/libexec/termux-api`). Local changes in
`termux-api.c`, marked `// VictorSuite add {@` / `modify {@`:

1. `am broadcast` targets `com.termux/com.termux.api.TermuxApiReceiver` (the receiver embedded in the
   VictorSuite APK, see `termux-api-lib`) instead of `com.termux.api/.TermuxApiReceiver`.
2. Waiting for the app to connect times out after 20 s with an error message instead of hanging forever.

Built as an executable by `../Android.mk` and installed to `$PREFIX/libexec` by `TermuxApiEmbed`
when Termux:API is turned on.
