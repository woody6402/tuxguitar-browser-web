# TuxGuitar Web Browser

Experimental web collection browser for TuxGuitar.

## Features

- Browse TuxGuitar-supported song files directly from web pages
- Automatic A–Z grouping with number of pieces
- Optional navigation to HTML sub-pages
- Sub-page navigation is disabled by default
- Same-host and external sub-pages are separated into `Links` and `Links extern`
- Same-host entries use `⌂`; external entries use `↗`
- HTML downloads are limited to 5 MiB
- Scanning is limited to 10,000 links per page; partial results remain available
- HTML is only parsed for links – no rendering or JavaScript execution
- A-Z song groups optionally contain filename-prefix folders when at least two files share the text before the first underscore
- Song files are opened using TuxGuitar's existing reader infrastructure
- Presets are loaded from `config/plugins/web-browser-presets.json` each time the configuration dialog is opened

## Usage

1. Add a new **Web** collection in the TuxGuitar browser.
2. Enter a name and the root URL.
3. Optionally enable **Follow links to sub-pages**.
4. Optionally enter a **Song link pattern** to treat matching links as songs. The pattern supports `*` for any text and `?` for one character, for example `*/download/`.
5. Browse and open the detected song files.

## Presets

On first use, the bundled `web-browser-presets.json` is copied to TuxGuitar's user
`config/plugins` directory. Edit that copy to add, remove, or change presets without
recompiling or restarting TuxGuitar. Close and reopen the Web configuration dialog to
reload it. Every entry supports `name`, `description`, `url`, `followLinks`, and
`songPattern`.

## Status

Experimental implementation.  
Website structures vary, so not every site may work correctly.
