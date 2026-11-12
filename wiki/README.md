# Wiki pages

Explainers meant to be shown in the app later (a WebView page over the current tab). Each page is one self-contained
HTML file in the app's look (Braun palette, Inter, light and dark through `prefers-color-scheme` or `data-theme`).

- `bins-and-bands.html`: how Android's EQ slots (block size) and AutoTune's 1/6- and 1/3-octave bands fit together,
  and what bigger or smaller settings change. Its numbers come from the same model as `dsp/DpBins.kt`. Published as
  https://claude.ai/artifact/LU9b6RdWX5cBLnFFzxTQAe (private).

Inter loads from Google Fonts; offline it falls back to the system font. To bundle a page in the app, copy it to
`app/src/main/assets/wiki/` and inline the font, or rely on the fallback.
