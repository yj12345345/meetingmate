package com.meetingmate.app.service.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmate.app.dto.recommendation.KeywordRecommendCategory;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResponse;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRoute;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRouteStep;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClientImpl implements OpenAiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.create("https://api.openai.com/v1");

    @Value("${meetingmate.openai-api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Override
    public List<PlaceRecommendResponse> recommend(String prompt) {
        String content = requestJsonContent(
                "너는 모임 장소 추천 전문가다. 반드시 JSON만 반환해야 한다.",
                prompt + """

                        응답 형식:
                        {
                          "recommendations": [
                            {
                              "name": "장소명",
                              "reason": "입력 조건에 맞는 이유 한 줄"
                            }
                          ]
                        }
                        """);

        return parsePlaceRecommendations(content);
    }

    @Override
    public KeywordRecommendResult recommendKeywords(String prompt) {
        String content = requestJsonContent(
                "You are a Korea meetup place recommender. Return valid JSON only.",
                prompt + """

                        Output schema:
                        {
                          "categories": [
                            {
                              "key": "One of RESTAURANT, CAFE, BAR, PC_ROOM, ACTIVITY",
                              "title": "A short Korean category title",
                              "description": "One short Korean sentence explaining why this category matters",
                              "places": [
                                {
                                  "name": "Official place name or branch name",
                                  "address": "Real South Korean road/jibun address that Kakao Map can recognize",
                                  "reason": "One short Korean sentence",
                                  "tags": ["#shortKoreanTag", "#shortKoreanTag"],
                                  "mapQuery": "Exact Kakao-search-friendly query: official place name + dong/gu"
                                }
                              ]
                            },
                            {
                              "key": "Next category key",
                              "title": "Next Korean category title",
                              "description": "One short Korean sentence",
                              "places": []
                            }
                          ],
                          "routes": [
                            {
                              "title": "Korean route title",
                              "summary": "One short Korean sentence summarizing the route flow",
                              "fitReason": "One short Korean sentence about who this route fits",
                              "steps": [
                                {
                                  "order": 1,
                                  "category": "RESTAURANT",
                                  "placeName": "Exactly reuse a place name from categories",
                                  "address": "Exactly reuse the same address from categories",
                                  "reason": "One short Korean sentence for why this step works here",
                                  "mapQuery": "Exactly reuse the same Kakao query from categories"
                                }
                              ]
                            }
                          ]
                        }

                        Hard rules:
                        - Follow the requested category keys and their exact order from the prompt.
                        - Provide 5 places per category when possible. If fewer than 5 are truly high-confidence, provide 3 or 4 instead of guessing.
                        - All descriptive text must be natural Korean, but think in English and follow these rules strictly.
                        - Never invent a venue, branch, street-market name, district nickname, or generic label.
                        - Never output placeholders or combinations like "Sinchon Korean Restaurant", "Jamsil Roastery Cafe", "props street", or "VR experience center".
                        - Use only venues you are highly confident are real and searchable on Kakao Map.
                        - If confidence is not high, prefer a famous well-known chain branch or famous landmark venue in the requested area instead of guessing.
                        - Do not create new branch names for chains.
                        - The trio of name, address, and mapQuery must refer to the same real venue.
                        - The address must be a concrete Korean address, not just district/dong level.
                        - mapQuery must be optimized for Kakao Map search and should use the official place name plus dong/gu.
                        - tags must be short Korean hashtags, 2 to 4 items.
                        - routes must be 2 to 3 clearly different route concepts, not simple reordered duplicates.
                        - Each route must reuse only places already listed in categories.
                        - If a place is uncertain, discard it and choose a safer, more famous, more searchable real place.
                        """);

        return parseKeywordRecommendationResult(content);
    }

    @Override
    public List<KeywordRecommendRoute> recommendSelectedRoutes(String prompt) {
        String content = requestJsonContent(
                "You are a Korea meetup route designer that may use only the user-selected venues. Return valid JSON only.",
                prompt + """

                        Output schema:
                        {
                          "routes": [
                            {
                              "title": "Korean route title",
                              "summary": "One short Korean sentence for the overall flow",
                              "fitReason": "One short Korean sentence for who this route fits",
                              "steps": [
                                {
                                  "order": 1,
                                  "category": "RESTAURANT",
                                  "placeName": "Exactly reuse the selected place name",
                                  "address": "Exactly reuse the selected address",
                                  "reason": "One short Korean sentence",
                                  "mapQuery": "Exactly reuse the selected mapQuery"
                                }
                              ]
                            }
                          ]
                        }

                        Hard rules:
                        - Create 2 to 3 meaningfully different route concepts, not simple rotations.
                        - Each route should feel distinct: for example conversation-first, activity-first, late-night extension, slow-and-long, etc.
                        - Do not add new places.
                        - Reuse placeName, address, and mapQuery exactly as given by the user.
                        - Do not duplicate the same place inside one route.
                        - Keep the route believable and practical for real movement.
                        """);

        try {
            JsonNode root = objectMapper.readTree(content);
            List<KeywordRecommendRoute> routes = parseRoutes(root.path("routes"));
            if (routes.isEmpty()) {
                throw new IllegalStateException("선택 장소 기반 코스 결과가 비어 있습니다.");
            }
            return routes;
        } catch (Exception exception) {
            throw new IllegalStateException("선택 장소 기반 코스 응답 파싱에 실패했습니다.", exception);
        }
    }

    private String requestJsonContent(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("meetingmate.openai-api-key가 설정되어 있지 않습니다.");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("")
                    .trim();

            if (content.isBlank()) {
                throw new IllegalStateException("OpenAI 응답 content가 비어 있습니다.");
            }

            return stripJsonFence(content);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "OpenAI 추천 호출에 실패했습니다. " + rootCauseMessage(exception),
                    exception
            );
        }
    }

    private List<PlaceRecommendResponse> parsePlaceRecommendations(String content) {
        try {
            JsonNode recommendations = objectMapper.readTree(content).path("recommendations");
            if (!recommendations.isArray()) {
                throw new IllegalStateException("recommendations 배열이 없습니다.");
            }

            List<PlaceRecommendResponse> results = new ArrayList<>();
            for (JsonNode item : recommendations) {
                String name = item.path("name").asText("").trim();
                String reason = item.path("reason").asText("").trim();
                if (!name.isBlank() && !reason.isBlank()) {
                    results.add(
                            PlaceRecommendResponse.builder()
                                    .name(name)
                                    .reason(reason)
                                    .build()
                    );
                }
            }

            if (results.isEmpty()) {
                throw new IllegalStateException("유효한 장소 추천 결과가 없습니다.");
            }

            return results.stream().limit(3).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("장소 추천 응답 파싱에 실패했습니다.", exception);
        }
    }

    private KeywordRecommendResult parseKeywordRecommendationResult(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<KeywordRecommendCategory> categories = parseCategories(root.path("categories"));
            List<KeywordRecommendRoute> routes = parseRoutes(root.path("routes"));
            List<KeywordRecommendResponse> recommendations = flattenRecommendations(categories);

            if (recommendations.isEmpty()) {
                recommendations = parseFlatRecommendations(root.path("recommendations"), null);
            }

            if (recommendations.isEmpty() && routes.isEmpty()) {
                throw new IllegalStateException("유효한 키워드 추천 결과가 없습니다.");
            }

            return KeywordRecommendResult.builder()
                    .categories(categories)
                    .routes(routes)
                    .recommendations(recommendations)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("키워드 추천 응답 파싱에 실패했습니다.", exception);
        }
    }

    private List<KeywordRecommendCategory> parseCategories(JsonNode categoriesNode) {
        if (!categoriesNode.isArray()) {
            return List.of();
        }

        List<KeywordRecommendCategory> categories = new ArrayList<>();
        for (JsonNode item : categoriesNode) {
            String key = item.path("key").asText("").trim();
            String title = item.path("title").asText("").trim();
            String description = item.path("description").asText("").trim();
            List<KeywordRecommendResponse> places = parseFlatRecommendations(item.path("places"), key);

            if (!key.isBlank() || !title.isBlank() || !places.isEmpty()) {
                categories.add(
                        KeywordRecommendCategory.builder()
                                .key(key)
                                .title(title)
                                .description(description)
                                .places(places)
                                .build()
                );
            }
        }

        return categories;
    }

    private List<KeywordRecommendRoute> parseRoutes(JsonNode routesNode) {
        if (!routesNode.isArray()) {
            return List.of();
        }

        List<KeywordRecommendRoute> routes = new ArrayList<>();
        for (JsonNode item : routesNode) {
            String title = item.path("title").asText("").trim();
            String summary = item.path("summary").asText("").trim();
            String fitReason = item.path("fitReason").asText("").trim();
            List<KeywordRecommendRouteStep> steps = parseRouteSteps(item.path("steps"));

            if (!title.isBlank() && !steps.isEmpty()) {
                routes.add(
                        KeywordRecommendRoute.builder()
                                .title(title)
                                .summary(summary)
                                .fitReason(fitReason)
                                .steps(steps)
                                .build()
                );
            }
        }

        return routes.stream().limit(3).toList();
    }

    private List<KeywordRecommendRouteStep> parseRouteSteps(JsonNode stepsNode) {
        if (!stepsNode.isArray()) {
            return List.of();
        }

        List<KeywordRecommendRouteStep> steps = new ArrayList<>();
        int fallbackOrder = 1;
        for (JsonNode item : stepsNode) {
            String placeName = item.path("placeName").asText("").trim();
            String address = item.path("address").asText("").trim();
            String mapQuery = item.path("mapQuery").asText("").trim();
            if (placeName.isBlank() || (address.isBlank() && mapQuery.isBlank())) {
                continue;
            }

            int order = item.path("order").canConvertToInt() ? item.path("order").asInt() : fallbackOrder;
            steps.add(
                    KeywordRecommendRouteStep.builder()
                            .order(order)
                            .category(item.path("category").asText("").trim())
                            .placeName(placeName)
                            .address(address)
                            .reason(item.path("reason").asText("").trim())
                            .mapQuery(mapQuery)
                            .build()
            );
            fallbackOrder += 1;
        }

        return steps;
    }

    private List<KeywordRecommendResponse> parseFlatRecommendations(JsonNode recommendationsNode, String defaultCategory) {
        if (!recommendationsNode.isArray()) {
            return List.of();
        }

        List<KeywordRecommendResponse> results = new ArrayList<>();
        for (JsonNode item : recommendationsNode) {
            String name = item.path("name").asText("").trim();
            String address = item.path("address").asText("").trim();
            String mapQuery = item.path("mapQuery").asText("").trim();
            if (name.isBlank() || (address.isBlank() && mapQuery.isBlank())) {
                continue;
            }

            results.add(
                    KeywordRecommendResponse.builder()
                            .category(firstNonBlank(item.path("category").asText("").trim(), defaultCategory))
                            .name(name)
                            .address(address)
                            .reason(item.path("reason").asText("").trim())
                            .tags(parseTags(item.path("tags")))
                            .mapQuery(mapQuery)
                            .build()
            );
        }

        return results;
    }

    private List<KeywordRecommendResponse> flattenRecommendations(List<KeywordRecommendCategory> categories) {
        Map<String, KeywordRecommendResponse> deduplicated = new LinkedHashMap<>();
        for (KeywordRecommendCategory category : categories) {
            if (category.getPlaces() == null) {
                continue;
            }
            for (KeywordRecommendResponse place : category.getPlaces()) {
                String key = (place.getName() + "|" + place.getAddress()).trim();
                deduplicated.putIfAbsent(key, place);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<String> parseTags(JsonNode tagsNode) {
        if (!tagsNode.isArray()) {
            return List.of();
        }

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (JsonNode tagNode : tagsNode) {
            String tag = tagNode.asText("").trim();
            if (tag.isBlank()) {
                continue;
            }
            if (!tag.startsWith("#")) {
                tag = "#" + tag;
            }
            tags.add(tag);
        }

        return new ArrayList<>(tags);
    }

    private String stripJsonFence(String content) {
        String sanitized = content.trim();
        if (sanitized.startsWith("```json")) {
            sanitized = sanitized.substring(7).trim();
        } else if (sanitized.startsWith("```")) {
            sanitized = sanitized.substring(3).trim();
        }

        if (sanitized.endsWith("```")) {
            sanitized = sanitized.substring(0, sanitized.length() - 3).trim();
        }

        return sanitized;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return "원인 메시지를 확인할 수 없습니다.";
        }

        return message.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
