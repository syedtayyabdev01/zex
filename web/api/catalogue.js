const API = "https://api.themoviedb.org/3";
const IMG = "https://image.tmdb.org/t/p";
const TOKEN = process.env.TMDB_API_READ_ACCESS_TOKEN;

function json(res, status, body) {
  res.status(status).setHeader("Cache-Control", "s-maxage=300, stale-while-revalidate=900").json(body);
}
async function tmdb(path) {
  const response = await fetch(API + path, {
    headers: { Authorization: "Bearer " + TOKEN, accept: "application/json" }
  });
  if (!response.ok) throw new Error("Catalogue provider returned " + response.status);
  return response.json();
}
module.exports = async (req, res) => {
  if (!TOKEN) return json(res, 503, { error: "Catalogue is not configured yet. Add TMDB_API_READ_ACCESS_TOKEN in Vercel Environment Variables." });
  const { action = "home", type = "movie", q = "", id = "", page = "1" } = req.query || {};
  if (!["movie", "tv", "all"].includes(type)) return json(res, 400, { error: "Invalid media type." });
  try {
    if (action === "search") {
      if (!q.trim()) return json(res, 200, { results: [] });
      const kind = type === "all" ? "multi" : type;
      const data = await tmdb("/search/" + kind + "?include_adult=false&query=" + encodeURIComponent(q.trim()) + "&page=" + Math.max(1, Math.min(500, Number(page) || 1)));
      return json(res, 200, data);
    }
    if (action === "details") {
      if (!/^\\d+$/.test(String(id))) return json(res, 400, { error: "A valid title ID is required." });
      const kind = type === "tv" ? "tv" : "movie";
      const data = await tmdb("/" + kind + "/" + id + "?append_to_response=videos,credits,watch%2Fproviders");
      return json(res, 200, data);
    }
    if (action === "season") {
      if (type !== "tv" || !/^\\d+$/.test(String(id)) || !/^\\d+$/.test(String(req.query.season))) return json(res, 400, { error: "Invalid season request." });
      return json(res, 200, await tmdb("/tv/" + id + "/season/" + req.query.season));
    }
    if (action === "home") {
      const requests = [
        tmdb("/trending/all/week?include_adult=false"),
        tmdb("/movie/popular?include_adult=false"),
        tmdb("/tv/popular?include_adult=false"),
        tmdb("/movie/top_rated?include_adult=false"),
        tmdb("/discover/tv?with_original_language=ja&sort_by=popularity.desc&include_adult=false"),
        tmdb("/movie/now_playing?include_adult=false")
      ];
      const [trending, movies, shows, top, anime, nowPlaying] = await Promise.all(requests);
      return json(res, 200, { trending: trending.results || [], movies: movies.results || [], shows: shows.results || [], top: top.results || [], anime: anime.results || [], nowPlaying: nowPlaying.results || [] });
    }
    if (action === "browse") {
      const endpoint = type === "tv" ? "tv" : "movie";
      return json(res, 200, await tmdb("/" + endpoint + "/popular?include_adult=false&page=" + Math.max(1, Math.min(500, Number(page) || 1))));
    }
    return json(res, 400, { error: "Unknown catalogue action." });
  } catch (error) {
    return json(res, 502, { error: error.message || "Catalogue request failed." });
  }
};
