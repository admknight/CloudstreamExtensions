# Archive Movies Provider

A Cloudstream 3 provider for public domain movies hosted on the Internet Archive (archive.org).

## Features

- **Search:** Search for movies by title.
- **Media Types:** Movies.
- **Quality:** Supports MP4 streams.
- **Language:** English (`en`).

## Implementation Details

The provider scrapes `archive.org/search` for movies and parses individual item pages to extract `.mp4` video links.

### Main URL
`https://archive.org`

## Development

To build this specific plugin:
```bash
./gradlew ArchiveMovies:make
```
To deploy to a device:
```bash
./gradlew ArchiveMovies:deployWithAdb
```
