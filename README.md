# 🎯 Adam Knight CloudStream Mega Repo

[![Update Aggregated Repository](https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml)

A dynamic CloudStream mega repository maintained by **Adam Knight**.

The catalog is rebuilt from multiple published CloudStream repositories, deduplicated, package-checked, and only then published.

## 🌐 Quick installation

### Preferred: shortcode

In CloudStream go to **Settings → Extensions → Add Repository** and enter:

    admknight

### Raw URL fallback

    https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/repo.json

## 📊 Current dashboard

Last successful refresh: **2026-10-04 00:44:01 UTC**

| Available | Package failures | Active sources | Failed sources | Added | Updated | Removed |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 253 | 3 | 13 | 0 | 0 | 253 | 0 |

### Source health

| Source | Index | Raw | Published | Package failed | Duplicate-skipped |
| --- | --- | ---: | ---: | ---: | ---: |
| [Phisher Repo](https://github.com/phisher98/cloudstream-extensions-phisher) | ✅ OK | 84 | 84 | 0 | 0 |
| [Cinephile](https://github.com/rockhero1234/cinephile) | ✅ OK | 5 | 3 | 0 | 2 |
| [CSX](https://github.com/SaurabhKaperwan/CSX) | ✅ OK | 5 | 5 | 0 | 0 |
| [NetMirror Extension](https://github.com/Sushan64/NetMirror-Extension) | ✅ OK | 1 | 1 | 0 | 0 |
| [Storm Extensions](https://github.com/Stormunblessed/storm-ext) | ✅ OK | 30 | 30 | 0 | 0 |
| [ReCloudStream Official Extensions](https://github.com/recloudstream/extensions) | ✅ OK | 5 | 5 | 0 | 0 |
| [Redowan CloudStream](https://github.com/redowan99/Redowan-CloudStream) | ✅ OK | 18 | 17 | 0 | 1 |
| [Vietnamese CloudStream Index](https://github.com/t23-02/cloudstream) | ✅ OK | 16 | 14 | 2 | 0 |
| [Nonton Indo](https://github.com/ExtremeBoyGG/nonton-indo) | ✅ OK | 16 | 16 | 0 | 0 |
| [Re-3arabi](https://github.com/Abodabodd/re-3arabi) | ✅ OK | 39 | 38 | 1 | 0 |
| [TheAlyss Repo](https://github.com/TheAlyss/cloudstream-AlyssRepo) | ✅ OK | 3 | 3 | 0 | 0 |
| [CakesTwix UK/UA](https://github.com/CakesTwix/cloudstream-extensions-uk) | ✅ OK | 21 | 21 | 0 | 0 |
| [CloudX-V2](https://github.com/Asm0d3usX/CloudX-V2) | ✅ OK | 18 | 16 | 0 | 2 |

### Package failures

A package is considered reachable when its published .cs3 URL responds successfully. This verifies package availability, not whether the underlying provider website still works at runtime.

| Plugin | Best source checked | Version | Result |
| --- | --- | ---: | --- |
| Aia2tv 2 | Re-3arabi | 3 | ❌ InvalidURL: URL can't contain control characters. '/Abodabodd/re-3arabi/builds/Aia2tv 2.cs3' (found at least ' ') |
| NguonCProvider | Vietnamese CloudStream Index | 8 | ❌ HTTPError: HTTP Error 404: Not Found |
| SubNhanhProvider | Vietnamese CloudStream Index | 9 | ❌ HTTPError: HTTP Error 404: Not Found |

## 📦 Available plugins

**253 plugins are currently published and package-reachable.**

| # | Plugin | Ver. | Maintainer | Lang | Types | Package | Source | Change |
| ---: | --- | ---: | --- | --- | --- | --- | --- | --- |
| 1 | **3isk** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 2 | **9kMovies** | 11 | Adam Knight | hi | Movie, TvSeries, NSFW | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 3 | **Aflaam** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 4 | **Akwam** | 3 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 5 | **AllMovieLandProvider** | 25 | Adam Knight | hi | Movie, TvSeries, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 6 | **AllWish** | 18 | Adam Knight | en | All | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 7 | **Alooytv** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 8 | **Anichi** | 28 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 9 | **Anichin** | 1 | Adam Knight | id | Anime, AnimeMovie | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 10 | **Anikage** | 8 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 11 | **AniKoto** | 6 | Adam Knight | en | Anime, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 12 | **Anilight** | 3 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 13 | **Anim3rb** | 1 | Adam Knight | ar | TvSeries, Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 14 | **Animasu** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 15 | **Anime-Phoenix** | 1 | Adam Knight | ar | TvSeries, Anime, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 16 | **Anime4up** | 1 | Adam Knight | ar | TvSeries, Movie, Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 17 | **Animeav1** | 9 | Adam Knight | mx | Movie, Anime, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 18 | **AnimeCloud** | 11 | Adam Knight | de | Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 19 | **AnimeDekhoProvider** | 70 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 20 | **Animedubhindi** | 9 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 21 | **AnimeflvIOProvider** | 2 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 22 | **AnimeflvProvider** | 5 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 23 | **AnimeHayProvider** | 11 | Adam Knight |  | Anime | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 24 | **AnimeIndo** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime, Movie | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 25 | **AnimeJlProvider** | 1 | Adam Knight | es | Movie, Anime | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 26 | **Animekhor** | 13 | Adam Knight | zh | AnimeMovie, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 27 | **Animenosub** | 11 | Adam Knight | en | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 28 | **AnimensionProvider** | 1 | Adam Knight | en | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 29 | **AnimeONProvider** | 41 | Adam Knight | uk | Anime, AnimeMovie, OVA | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 30 | **AnimePahe** | 42 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 31 | **Animerco** | 3 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 32 | **AnimeRift** | 1 | Adam Knight | ar | TvSeries, Anime, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 33 | **Animesalt** | 18 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 34 | **AnimeUAProvider** | 10 | Adam Knight | uk | Anime, AnimeMovie, OVA | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 35 | **AnimeVietsubProvider** | 14 | Adam Knight |  | Anime | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 36 | **Animewitcher** | 3 | Adam Knight | ar | Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 37 | **Animexin** | 16 | Adam Knight | en | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 38 | **AniSnatch** | 2 | Adam Knight | en | Anime, AnimeMovie, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 39 | **AnitubeinuaProvider** | 21 | Adam Knight | uk | Anime, AnimeMovie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 40 | **AniVortex** | 10 | Adam Knight | hi | Anime, AnimeMovie, OVA, TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 41 | **AniwatchProvider** | 1 | Adam Knight | en | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 42 | **AniwaveProvider** | 20 | Adam Knight | en | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 43 | **Aniworld** | 15 | Adam Knight | de | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 44 | **Anizone** | 10 | Adam Knight | en | Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 45 | **BambooUAProvider** | 13 | Adam Knight | uk | Anime, AsianDrama | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 46 | **BanglaPlex** | 8 | Adam Knight | bn | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 47 | **BdixBdipTV** | 10 | Adam Knight | bn | Live | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 48 | **BdixCircleftp** | 27 | Adam Knight | bn | Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, AsianDrama, Others, Documentary | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 49 | **BdixCloudTV** | 6 | Adam Knight | bn | Live | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 50 | **BdixDflix** | 10 | Adam Knight | bn | Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, AsianDrama, Others, Documentary | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 51 | **BdixDhakaFlix** | 8 | Adam Knight | bn | Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, AsianDrama, Others, Documentary | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 52 | **BdixICCFtp** | 3 | Adam Knight | bn | Movie, TvSeries, Anime, AnimeMovie, OVA, Cartoon, AsianDrama, Others, Documentary | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 53 | **BdixMyMovieBazarTV** | 6 | Adam Knight | bn | Live | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 54 | **BdixRoarZoneTV** | 6 | Adam Knight | bn | Live | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 55 | **BflixProvider** | 8 | Adam Knight | en |  | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 56 | **BingedReview** | 3 | Adam Knight |  | Movie | ✅ Reachable | Cinephile | 🔄 Updated |
| 57 | **BluPhimProvider** | 16 | Adam Knight |  | TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 58 | **Bollyflix** | 33 | Adam Knight | hi | TvSeries, Movie, AsianDrama, Anime | ✅ Reachable | CSX | 🔄 Updated |
| 59 | **Bristege** | 1 | Adam Knight | ar | Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 60 | **CablevisionHdProvider** | 4 | Adam Knight | es | Live | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 61 | **Cee** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 62 | **Chikianimation** | 2 | Adam Knight | zh | AnimeMovie, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 63 | **CikavaIdeyaProvider** | 5 | Adam Knight | uk | Cartoon, TvSeries, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 64 | **CimaClub** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 65 | **Cimalight** | 1 | Adam Knight | ar | TvSeries, Movie, Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 66 | **Cimatn** | 1 | Adam Knight | ar | TvSeries, Movie, Drama | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 67 | **CinecalidadProvider** | 5 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 68 | **Cinefreak** | 16 | Adam Knight | bn | Movie, TvSeries, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 69 | **Cinemacity** | 27 | Adam Knight | en | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 70 | **cinemana** | 4 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 71 | **CineStream** | 487 | Adam Knight | en | TvSeries, Movie, AsianDrama, Anime, Torrent | ✅ Reachable | CSX | 🔄 Updated |
| 72 | **Cinevood** | 11 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Cinephile | 🔄 Updated |
| 73 | **CoaninetProvider** | 2 | Adam Knight | uk | TvSeries | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 74 | **Coflix** | 19 | Adam Knight | fr | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 75 | **ComamosRamenProvider** | 3 | Adam Knight | es | AsianDrama | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 76 | **Comix** | 2 | Adam Knight | en | Others, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 77 | **CuevanaProvider** | 9 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 78 | **DailymotionProvider** | 4 | Adam Knight |  | Others | ✅ Reachable | ReCloudStream Official Extensions | 🔄 Updated |
| 79 | **Desicinemas** | 19 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 80 | **dima-toon** | 1 | Adam Knight | ar | TvSeries, Anime | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 81 | **Donghuastream** | 22 | Adam Knight | zh | Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 82 | **Donghub** | 1 | Adam Knight | id | Anime, AnimeMovie | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 83 | **DoraBash** | 14 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 84 | **DoramasFlixProvider** | 5 | Adam Knight | es | AsianDrama | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 85 | **DoramasYTProvider** | 5 | Adam Knight | es | AsianDrama | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 86 | **DoramyWorldProvider** | 2 | Adam Knight | uk | AsianDrama, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 87 | **DudeFilms** | 13 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 88 | **Dutamovie** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 89 | **Egydead** | 1 | Adam Knight | ar | Movie, TvSeries, Anime, AsianDrama | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 90 | **Elif** | 1 | Adam Knight | ar | TvSeries, Anime, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 91 | **EmwBD** | 16 | Adam Knight | bn | Movie, TvSeries, AnimeMovie, AsianDrama, NSFW | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 92 | **EneyidaProvider** | 19 | Adam Knight | uk | Anime, TvSeries, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 93 | **EntrepeliculasyseriesProvider** | 6 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 94 | **eseek** | 3 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 95 | **EstrenosDoramasProvider** | 2 | Adam Knight | es | AsianDrama | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 96 | **Faselhd** | 3 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 97 | **Fibwatch** | 11 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 98 | **FilmapikProvider** | 21 | Adam Knight | id | Movie, TvSeries, AsianDrama, Anime | ✅ Reachable | TheAlyss Repo | 🔄 Updated |
| 99 | **Filmkita** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 100 | **Filmlokal** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 101 | **Fivemovierulz** | 8 | Adam Knight | hi | TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 102 | **FootReplays** | 6 | Adam Knight | en | Others | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 103 | **FourKHDHub** | 41 | Adam Knight | en | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 104 | **FullReplays** | 6 | Adam Knight | en | Others | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 105 | **FullyMaza** | 7 | Adam Knight | en | Movie, TvSeries, AnimeMovie, Cartoon | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 106 | **GoldenAudiobooks** | 1 | Adam Knight | en | Others | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 107 | **Goojara** | 5 | Adam Knight | en | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 108 | **Hanime** | 3 | Adam Knight | en | NSFW | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 109 | **HDhub4u** | 56 | Adam Knight | hi | Movie, TvSeries, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 110 | **Hdmovie2** | 6 | Adam Knight | hi | TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 111 | **HentaiUkrProvider** | 6 | Adam Knight | uk | NSFW | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 112 | **HHkungfuProvider** | 5 | Adam Knight |  | Anime, AnimeMovie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 113 | **HHPandaProvider** | 12 | Adam Knight |  | Anime | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 114 | **HiAnime** | 2 | Adam Knight | en | Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 115 | **Hindmoviez** | 17 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 116 | **Idlix** | 2 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 117 | **IdlixProvider** | 16 | Adam Knight | id | TvSeries, Movie, Anime, AsianDrama | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 118 | **Indomax** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 119 | **InternetArchiveProvider** | 1 | Adam Knight |  | Others | ✅ Reachable | ReCloudStream Official Extensions | 🔄 Updated |
| 120 | **InvidiousProvider** | 9 | Adam Knight |  | Others | ✅ Reachable | ReCloudStream Official Extensions | 🔄 Updated |
| 121 | **IPTVPlayer** | 9 | Adam Knight | hi | Live | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 122 | **IStreamFlare** | 6 | Adam Knight | hi | AsianDrama, TvSeries, Anime, Movie, Cartoon, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 123 | **Jellyfin** | 7 | Adam Knight | en | AsianDrama, TvSeries, Anime, Movie, Cartoon, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 124 | **JKAnimeProvider** | 8 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 125 | **Kartoons** | 5 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 126 | **Kawanfilm** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 127 | **Kickassanime** | 27 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 128 | **KinostrainProvider** | 2 | Adam Knight | uk | TvSeries, Cartoon, Movie, Anime | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 129 | **KinoTronProvider** | 17 | Adam Knight | uk | Cartoon, TvSeries, Movie, Anime | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 130 | **KinoVezhaProvider** | 14 | Adam Knight | uk | Cartoon, TvSeries, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 131 | **KisskhProvider** | 22 | Adam Knight | en | AsianDrama, TvSeries, Anime, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 132 | **KKPhimProvider** | 10 | Adam Knight |  | Anime, TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 133 | **KlikXXi** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 134 | **KlonTVProvider** | 21 | Adam Knight | uk | Anime, TvSeries, Cartoon, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 135 | **Krmzy** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 136 | **Kuramanime** | 2 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 137 | **Kuronime** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 138 | **LACartoonsProvider** | 3 | Adam Knight | es | Cartoons, TvSeries | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 139 | **Latanime** | 5 | Adam Knight | mx | Movie, Anime, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 140 | **LatAnimeProvider** | 2 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 141 | **LayarKaca** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 142 | **LayarKacaProvider** | 10 | Adam Knight | id | AsianDrama, TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 143 | **LayarWarna** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 144 | **Lodynet** | 2 | Adam Knight | ar | Movie, tvTypes | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 145 | **MassTamilanProvider** | 9 | Adam Knight | ta | Music, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 146 | **Megakino** | 6 | Adam Knight | de | Movie,Anime,Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 147 | **Microtv** | 3 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 148 | **MidasXXi** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 149 | **MonoschinosProvider** | 6 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 150 | **MovieBox** | 2 | Adam Knight | id | Anime, Movie, TvSeries | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 151 | **Moviebox** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 152 | **MovieBoxProvider** | 36 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 153 | **MovieboxProvider** | 9 | Adam Knight | id | TvSeries, Movie, AsianDrama | ✅ Reachable | TheAlyss Repo | 🔄 Updated |
| 154 | **Movies4u** | 17 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 155 | **MoviesDrive** | 33 | Adam Knight | hi | TvSeries, Movie, AsianDrama, Anime | ✅ Reachable | CSX | 🔄 Updated |
| 156 | **Moviesmod** | 33 | Adam Knight |  | TvSeries, Movie, AsianDrama, Anime | ✅ Reachable | CSX | 🔄 Updated |
| 157 | **MoviPK** | 4 | Adam Knight | en | Movie, TvSeries | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 158 | **Mp4Moviez** | 3 | Adam Knight | hi | Movie, TvSeries, NSFW | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 159 | **MPlayerProvider** | 9 | Adam Knight | hi | AsianDrama, TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 160 | **MultiMoviesProvider** | 54 | Adam Knight | hi | Movie, TvSeries, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 161 | **MundoDonghuaProvider** | 3 | Adam Knight | es | Anime, OVA, AnimeMovie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 162 | **MyCimaProvider** | 1 | Adam Knight | ar | Movie, TvSeries, Anime, AsianDrama | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 163 | **Nekopoi** | 1 | Adam Knight | id | NSFW | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 164 | **Netcinez** | 5 | Adam Knight | pt-br | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 165 | **Netmirror** | 46 | Adam Knight | hi | Movie, TvSeries | ✅ Reachable | NetMirror Extension | 🔄 Updated |
| 166 | **Ngefilm** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 167 | **Nimegami** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 168 | **Nomat** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 169 | **ObejrzyjTo** | 3 | Adam Knight | pl | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 170 | **OHiTVProvider** | 2 | Adam Knight |  | TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 171 | **OHLI24** | 7 | Adam Knight | ko | AsianDrama, TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 172 | **OnePace** | 23 | Adam Knight | en | Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 173 | **OneTouchTV** | 5 | Adam Knight | en | AsianDrama, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 174 | **OPhimProvider** | 10 | Adam Knight |  | Anime, TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 175 | **Oploverz** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 176 | **Otakudesu** | 1 | Adam Knight | id | AnimeMovie, OVA, Anime | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 177 | **Pahe** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 178 | **PeliculasFlixProvider** | 1 | Adam Knight | es | Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 179 | **PelispediaProvider** | 5 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 180 | **Pelisplus4KProvider** | 6 | Adam Knight | es | Movie, TvSeries, AsianDrama, Anime | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 181 | **PelisplusHDProvider** | 5 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 182 | **PelisplusSOProvider** | 3 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 183 | **Pencurimovie** | 8 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 184 | **PhimLongTiengProvider** | 12 | Adam Knight |  | TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 185 | **PhimMoiProvider** | 9 | Adam Knight |  | TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 186 | **PhimTuoiThoProvider** | 5 | Adam Knight |  | Anime, TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 187 | **Pinoymoviepedia** | 5 | Adam Knight | fil | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 188 | **Piratexplay** | 5 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 189 | **PlayhubProvider** | 1 | Adam Knight | es | Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 190 | **Pmsm** | 8 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 191 | **PublicSportsIPTV** | 5 | Adam Knight | en | Live | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 192 | **Pusatmovie** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 193 | **QuickIPTV** | 8 | Adam Knight | en | Live | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 194 | **Reanime** | 3 | Adam Knight | en | AnimeMovie, Anime, OVA | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 195 | **Rebahin** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 196 | **Replaymatch** | 2 | Adam Knight | en | Movie, Others, live | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 197 | **RingZ** | 12 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 198 | **Sarangfilm** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 199 | **Savefilm** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 200 | **SemiRebahin** | 1 | Adam Knight | id | NSFW | ✅ Reachable | Nonton Indo | 🔄 Updated |
| 201 | **SerialnoProvider** | 13 | Adam Knight | uk | Cartoon, TvSeries | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 202 | **SeriesflixProvider** | 2 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 203 | **SeriesMetroProvider** | 3 | Adam Knight | es | TvSeries, Movie | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 204 | **Shahid4u** | 2 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 205 | **Shahidwbas** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 206 | **ShowBox** | 10 | Adam Knight | en | AsianDrama, Anime, TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 207 | **SimpsonsUATvProvider** | 5 | Adam Knight | uk | Cartoon, TvSeries | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 208 | **SkymoviesHD** | 1 | Adam Knight | en | Movie, TvSeries, NSFW | ✅ Reachable | Cinephile | 🔄 Updated |
| 209 | **SoloLatinoProvider** | 1 | Adam Knight | es | Movie, TvSeries, Anime, Cartoon | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 210 | **StreamPlay** | 685 | Adam Knight | en | AsianDrama, TvSeries, Anime, Movie, Cartoon, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 211 | **StremioAddon** | 16 | Adam Knight | en | TvSeries, Movie, Torrent | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 212 | **StremioX** | 27 | Adam Knight | en | TvSeries, Movie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 213 | **SuperStream** | 38 | Adam Knight | en | AsianDrama, TvSeries, Anime, Movie, Cartoon, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 214 | **SyncPlugin** | 3 | Adam Knight | uk | Others | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 215 | **Syria-live** | 1 | Adam Knight | ar | TvSeries, Live | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 216 | **Tamilblasters** | 12 | Adam Knight | ta | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 217 | **TheMoviesFlix** | 6 | Adam Knight | hi | Movie, TvSeries, NSFW | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 218 | **TioAnimeProvider** | 3 | Adam Knight | es | Anime, OVA | ✅ Reachable | Storm Extensions | 🔄 Updated |
| 219 | **ToonHub** | 12 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 220 | **Toonstream** | 10 | Adam Knight | hi | AnimeMovie, Anime, Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 221 | **ToonTales** | 5 | Adam Knight | hi | Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 222 | **Topcartoons** | 5 | Adam Knight | hi | Cartoon | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 223 | **Topcinema** | 1 | Adam Knight | ar | Movie, tvTypes | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 224 | **Topstreamfilm** | 9 | Adam Knight | de | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 225 | **TorraStream** | 97 | Adam Knight | en | Movie, Torrent, AsianDrama, TvSeries, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 226 | **TukTukcima** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 227 | **TuniflexBlog** | 1 | Adam Knight | ar | TvSeries, Anime, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 228 | **Tuniflix** | 1 | Adam Knight | ar | TvSeries, Movie, Drama | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 229 | **TVgarden** | 1 | Adam Knight | ar | TvSeries, Movie, Live | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 230 | **TvPhimProvider** | 11 | Adam Knight |  | Anime, TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 231 | **TwitchProvider** | 2 | Adam Knight |  | Live | ✅ Reachable | ReCloudStream Official Extensions | 🔄 Updated |
| 232 | **UAFlixProvider** | 20 | Adam Knight | uk | Anime, Cartoon, Movie, TvSeries | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 233 | **UakinoProvider** | 32 | Adam Knight | uk | Anime, TvSeries, Movie, AsianDrama | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 234 | **UASerialsProProvider** | 27 | Adam Knight | uk | Anime, Cartoon, Movie, TvSeries | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 235 | **UFDubProvider** | 12 | Adam Knight | uk | Anime, AnimeMovie, AsianDrama, Cartoon, TvSeries, Movie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 236 | **UHDmoviesProvider** | 41 | Adam Knight | en | Movie, TvSeries | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 237 | **Ultima** | 65 | Adam Knight | en | All | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 238 | **UnimayProvider** | 13 | Adam Knight | uk | Anime, AnimeMovie | ✅ Reachable | CakesTwix UK/UA | 🔄 Updated |
| 239 | **VegaMovies** | 82 | Adam Knight | hi | TvSeries, Movie, AsianDrama, Anime | ✅ Reachable | CSX | 🔄 Updated |
| 240 | **VipPhimProvider** | 9 | Adam Knight |  | TvSeries, Movie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 241 | **Viu** | 1 | Adam Knight | ar | TvSeries, Movie, Drama | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 242 | **WatchMoviesPk** | 3 | Adam Knight | hi | TvSeries, Movie | ✅ Reachable | Redowan CloudStream | 🔄 Updated |
| 243 | **Wecima** | 1 | Adam Knight | ar | Movie, tvTypes | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 244 | **WGFilm21** | 1 | Adam Knight | id | Movie, TvSeries | ✅ Reachable | CloudX-V2 | 🔄 Updated |
| 245 | **Witanime** | 1 | Adam Knight | ar | TvSeries, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 246 | **XDMovies** | 26 | Adam Knight | en | AsianDrama, TvSeries, Anime, Movie, Cartoon, AnimeMovie | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 247 | **Yacintv** | 1 | Adam Knight | ar | TvSeries, Live, Movie | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 248 | **YanHH3DProvider** | 16 | Adam Knight |  | Anime, AnimeMovie | ✅ Reachable | Vietnamese CloudStream Index | 🔄 Updated |
| 249 | **YlnimeProvider** | 6 | Adam Knight | id | Anime, TvSeries, Movie | ✅ Reachable | TheAlyss Repo | 🔄 Updated |
| 250 | **Youtube** | 2 | Adam Knight | ar | Movie, TvSeries, Live, Anime, Music, Documentary | ✅ Reachable | Re-3arabi | 🔄 Updated |
| 251 | **YoutubeProvider** | 1 | Adam Knight |  | Other, Live, TvSeries | ✅ Reachable | ReCloudStream Official Extensions | 🔄 Updated |
| 252 | **YTS** | 11 | Adam Knight | en | Movie, Torrent | ✅ Reachable | Phisher Repo | 🔄 Updated |
| 253 | **Zinkmovies** | 12 | Adam Knight | hi | Movie, TvSeries, Anime | ✅ Reachable | Phisher Repo | 🔄 Updated |

## 🔁 Duplicate handling

When the same plugin is published by more than one source, the highest version is preferred; source priority breaks version ties. Unreachable candidates are skipped in favor of a reachable alternative when possible.

| Plugin | Selected | Skipped |
| --- | --- | --- |
| BanglaPlex | Phisher Repo v8 | Redowan CloudStream v1 (lower version) |
| Idlix | Nonton Indo v2 | CloudX-V2 v1 (lower version) |
| Mp4Moviez | Redowan CloudStream v3 | Cinephile v1 (lower version) |
| Pencurimovie | Phisher Repo v8 | CloudX-V2 v1 (lower version) |
| Tamilblasters | Phisher Repo v12 | Cinephile v3 (lower version) |

## 🧭 Status files

- STATUS.md on the builds branch — detailed current health report
- BUILD_HISTORY.md on the builds branch — successful publication history
- merge-report.json on the builds branch — machine-readable build report
- provenance.json on the builds branch — original source/author provenance retained for maintenance

## 🛡️ Publication safety

Production is not overwritten when an active source index fails. A large unexpected catalog drop is also blocked by the safety gate, so the last known-good catalog remains live.

## 🧰 Repository architecture

This is an aggregator, not a source-code fork. Published CloudStream package URLs are consumed from upstream indexes; the installer-facing catalog is branded as maintained by Adam Knight, while original provenance is retained separately for maintenance and attribution.

## ⚖️ Disclaimer

This repository is an index/aggregation project and does not host video or media content. Package reachability does not guarantee that every third-party provider website is operational at runtime.

*Maintained by Adam Knight*

