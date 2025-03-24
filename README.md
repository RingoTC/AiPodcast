# AiPodcast

An Android application that integrates with the New York Times Top Stories API.

## Setup

1. Get an API key from [New York Times Developer Portal](https://developer.nytimes.com/apis)
2. Create a `local.properties` file in the project root if it doesn't exist
3. Add your API key to `local.properties`:
   ```
   nyt.api.key=YOUR_API_KEY_HERE
   ```

## News API Usage

The app provides a simple interface to the NYTimes Top Stories API. Here's how to use it:

```java
// Initialize the service with your API key
NewsService newsService = new NYTimesNewsService(BuildConfig.NYT_API_KEY);

// Get news by category
newsService.getNewsByCategory(NewsCategory.ARTS)
    .thenAccept(articles -> {
        // Handle the list of articles
        for (NewsArticle article : articles) {
            System.out.println(article.getTitle());
            System.out.println(article.getAbstract());
            System.out.println(article.getUrl());
        }
    })
    .exceptionally(throwable -> {
        // Handle errors
        throwable.printStackTrace();
        return null;
    });
```

### Available Categories

The following news categories are supported:
- ARTS
- HOME
- SCIENCE
- US
- WORLD

Each category maps to a specific NYTimes API endpoint.

### Article Information

Each `NewsArticle` object contains:
- Title
- Abstract
- URL
- Section
- Published Date

## Testing

Run unit tests with:
```bash
./gradlew test
```

## Security

The API key is stored in `local.properties` and accessed via BuildConfig, keeping it out of version control.

Project Proposal:
https://docs.google.com/document/d/1ZL7uklKWlAS4aZ865cyA7mOutcn1EIaqQq2Jg8FXR0s/edit?usp=sharing