# TuxGuitar Web Browser

Experimental web collection browser for TuxGuitar.

## Features

- Browse TuxGuitar-supported song files directly from web pages
- Automatic A–Z grouping with number of pieces
- Optional navigation to HTML sub-pages
- Sub-page navigation is disabled by default
- Sub-pages are collected in a separate `[Links]` folder
- Navigation is restricted to the same host
- HTML is only parsed for links – no rendering or JavaScript execution
- Song files are opened using TuxGuitar's existing reader infrastructure

## Usage

1. Add a new **Web** collection in the TuxGuitar browser.
2. Enter a name and the root URL.
3. Optionally enable **Follow links to sub-pages**.
4. Browse and open the detected song files.

## Status

Experimental implementation.  
Website structures vary, so not every site may work correctly.
