# ZEX BOX web

ZEX BOX is a responsive discovery frontend for movies, TV series and anime. The catalogue is powered by TMDB metadata. It does not scrape streaming sites or provide unauthorized video sources.

## Deploy on Vercel

The Vercel project should use `web` as its Root Directory. The API route is `/api/catalogue`.

1. Create a TMDB account and request an API Read Access Token in the account API settings.
2. In Vercel → Project → Settings → Environment Variables, add:
   - Name: `TMDB_API_READ_ACCESS_TOKEN`
   - Value: your TMDB API Read Access Token
   - Environments: Production, Preview, Development as needed
3. Redeploy after adding the variable.

Keep the token server-side. Do not put it in the HTML, browser JavaScript, GitHub, or a public environment variable.

## API actions

- `GET /api/catalogue?action=home` — trending, popular movies, popular series, Japanese-language TV discovery, top rated, now playing.
- `GET /api/catalogue?action=browse&type=movie|tv&page=1` — popular catalogue page.
- `GET /api/catalogue?action=search&type=all&q=...` — multi-search.
- `GET /api/catalogue?action=details&type=movie|tv&id=...` — metadata and credits.
- `GET /api/catalogue?action=season&type=tv&id=...&season=1` — TV episode guide.

## Playback

Playback is intentionally not faked. TMDB provides metadata, not streaming rights or playable media. A playback service must be separately configured using a provider API for which ZEX has explicit distribution/streaming authorization. Until then, title details show the official availability lookup link and clearly indicate that playback is not configured.

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB. Metadata and images are provided by TMDB. Follow the current TMDB API terms and branding requirements before public/commercial release.
