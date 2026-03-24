package com.meetingmate.app.service;

import com.meetingmate.app.domain.place.PlaceCandidate;
import com.meetingmate.app.dto.recommendation.KeywordRecommendCategory;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResponse;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRoute;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRouteStep;
import com.meetingmate.app.dto.recommendation.PlaceRecommendRequest;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import com.meetingmate.app.dto.recommendation.SelectedRouteRecommendRequest;
import com.meetingmate.app.dto.recommendation.SelectedRouteRecommendResult;
import com.meetingmate.app.repository.PlaceCandidateRepository;
import com.meetingmate.app.service.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int CATEGORY_PLACE_TARGET = 5;
    private static final List<String> DEFAULT_CATEGORY_ORDER = List.of("RESTAURANT", "CAFE", "ACTIVITY");
    private static final List<String> CATEGORY_ORDER = List.of("PC_ROOM", "RESTAURANT", "CAFE", "BAR", "KARAOKE", "SHOPPING", "ACTIVITY");
    private static final Map<String, String> CATEGORY_TITLES = Map.of(
            "RESTAURANT", "식당",
            "CAFE", "카페",
            "BAR", "술집",
            "PC_ROOM", "PC방",
            "KARAOKE", "코인노래방",
            "SHOPPING", "쇼핑",
            "ACTIVITY", "놀거리"
    );

    private final OpenAiClient openAiClient;
    private final PlaceCandidateRepository placeCandidateRepository;

    // MVP용 메모리 캐시
    private final Map<String, List<PlaceRecommendResponse>> cache = new HashMap<>();

    public List<PlaceRecommendResponse> recommend(PlaceRecommendRequest request) {
        String cacheKey = request.getMeetingType()
                + "|" + request.getCategory()
                + "|" + request.getPreferences();

        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        String prompt = """
        모임 유형: %s
        카테고리: %s
        선호 조건: %s

        위 조건에 맞는 장소 유형을 3개 추천하고,
        각 추천마다 한 줄 이유를 작성해줘.
        """.formatted(
                request.getMeetingType(),
                request.getCategory(),
                String.join(", ", request.getPreferences())
        );

        List<PlaceRecommendResponse> result;
        try {
            result = openAiClient.recommend(prompt);
        } catch (RuntimeException exception) {
            result = fallbackPlaceRecommendations(request);
        }

        cache.put(cacheKey, result);
        return result;
    }

    public KeywordRecommendResult recommendByKeyword(
            String keyword,
            String meetingType,
            String mood,
            String location,
            String planHint
    ) {
        String normalizedKeyword = firstNonBlank(keyword, location, mood, meetingType);
        if (normalizedKeyword.isBlank()) {
            throw new IllegalArgumentException("keyword는 필수입니다.");
        }

        String area = firstNonBlank(location, keyword, normalizedKeyword);
        List<String> requestedCategories = resolveRequestedCategories(planHint);
        List<PlaceCandidate> dbMatches =
                placeCandidateRepository.findTop5ByNameContainingIgnoreCaseOrderByIdDesc(normalizedKeyword);

        String warning = null;
        try {
            KeywordRecommendResult aiResult = normalizeKeywordResult(
                    openAiClient.recommendKeywords(buildKeywordPrompt(normalizedKeyword, meetingType, mood, area, planHint, requestedCategories, dbMatches)),
                    normalizedKeyword,
                    meetingType,
                    mood,
                    area,
                    requestedCategories
            );

            if (!aiResult.getRecommendations().isEmpty()) {
                return buildResult(aiResult, "AI", null);
            }
        } catch (RuntimeException exception) {
            warning = exception.getMessage();
        }

        if (!dbMatches.isEmpty()) {
            return buildResult(
                    buildDbKeywordResult(dbMatches, normalizedKeyword, meetingType, mood, area, requestedCategories),
                    "DB",
                    warning == null ? "AI 추천 대신 기존 후보 데이터를 바탕으로 코스를 구성했습니다." : warning
            );
        }

        return buildResult(
                buildFallbackKeywordResult(normalizedKeyword, meetingType, mood, area, requestedCategories),
                "FALLBACK",
                warning == null ? "AI 추천 대신 기본 코스를 제공했습니다." : warning
        );
    }

    public SelectedRouteRecommendResult recommendRoutesFromSelections(SelectedRouteRecommendRequest request) {
        String keyword = firstNonBlank(request.getKeyword(), request.getLocation(), request.getMood(), request.getMeetingType());
        String area = firstNonBlank(request.getLocation(), request.getKeyword(), keyword);
        List<KeywordRecommendResponse> selectedPlaces = normalizeSelectedPlaces(
                request.getSelectedPlaces(),
                keyword,
                request.getMeetingType(),
                request.getMood(),
                area
        );

        if (selectedPlaces.size() < 2) {
            throw new IllegalArgumentException("선택한 장소가 2곳 이상 있어야 코스를 다시 추천할 수 있습니다.");
        }

        String warning = null;
        try {
            List<KeywordRecommendRoute> aiRoutes = normalizeSelectedRoutes(
                    openAiClient.recommendSelectedRoutes(
                            buildSelectedRoutePrompt(keyword, request.getMeetingType(), request.getMood(), area, request.getPlanHint(), selectedPlaces)
                    ),
                    selectedPlaces,
                    request.getMeetingType(),
                    request.getMood(),
                    area,
                    keyword
            );

            if (!aiRoutes.isEmpty()) {
                return SelectedRouteRecommendResult.builder()
                        .selectedPlaces(selectedPlaces)
                        .routes(aiRoutes)
                        .source("AI")
                        .warning(null)
                        .build();
            }
        } catch (RuntimeException exception) {
            warning = exception.getMessage();
        }

        return SelectedRouteRecommendResult.builder()
                .selectedPlaces(selectedPlaces)
                .routes(buildSelectedPlaceRoutes(selectedPlaces, request.getMeetingType(), request.getMood()))
                .source("FALLBACK")
                .warning(warning == null ? "AI 대신 선택한 장소로 기본 코스를 구성했습니다." : warning)
                .build();
    }

    private KeywordRecommendResult buildResult(KeywordRecommendResult content, String source, String warning) {
        return KeywordRecommendResult.builder()
                .recommendations(content.getRecommendations())
                .categories(content.getCategories())
                .routes(content.getRoutes())
                .source(source)
                .warning(warning)
                .build();
    }

    private String buildKeywordPrompt(
            String keyword,
            String meetingType,
            String mood,
            String area,
            String planHint,
            List<String> requestedCategories,
            List<PlaceCandidate> dbMatches
    ) {
        String knownCandidates = dbMatches.isEmpty()
                ? "없음"
                : dbMatches.stream()
                .map(place -> "- %s [%s]".formatted(place.getName(), categoryTitle(normalizeCategoryKey(place.getCategory()))))
                .collect(Collectors.joining("\n"));
        String requestedCategorySummary = requestedCategories.stream()
                .map(categoryKey -> "%s(%s)".formatted(categoryKey, categoryTitle(categoryKey)))
                .collect(Collectors.joining(", "));

        return """
                User request:
                - Main keyword: %s
                - Meeting type: %s
                - Low-priority vibe hint: %s
                - Preferred area: %s
                - Highest-priority concrete flow / menu / style / exclusions: %s
                - Requested category keys in order: %s

                Internal candidate data from the app:
                %s

                Instructions:
                1. Recommend only the requested category keys and keep the exact order.
                2. For each category, return 5 real venues when possible. If fewer than 5 truly high-confidence venues exist, return 3 or 4 instead of guessing.
                3. Treat the concrete flow/menu/style/exclusion request as the highest-priority constraint.
                4. Treat meeting type and vibe only as secondary tie-breakers. Never let a vague vibe word override explicit cuisine, dessert, or route instructions.
                5. If the user asks for western, Italian, pasta, steak, bistro, cake, dessert, bakery, wine, samgyeopsal, coin karaoke, or similar specific styles, stay inside that style. Do not switch to unrelated chicken, seafood, soup restaurants, generic Korean diners, wholesalers, gardens, or convenience-style venues unless explicitly requested.
                6. Respect exclusions literally. If the user says "not franchise", "not chicken", "not noisy bars", or similar, never recommend those.
                7. Every venue must include a short Korean reason and short Korean hashtags.
                8. Create 2 to 3 route concepts that feel genuinely different, not trivial reorderings.
                9. Never invent a venue, never invent a branch name, and never use a generic label as if it were a real venue.
                10. Absolutely avoid vague names such as "Sinchon Korean restaurant", "Jamsil roastery cafe", "props street", or "VR experience center".
                11. If you are not highly confident a venue is real, do not output it.
                12. If internal candidate data exists, reuse those exact names first when they are relevant.
                13. Prefer famous, searchable, high-confidence real places over creative or obscure guesses.
                14. The name, address, and mapQuery must point to the same real venue.
                15. The address must be a concrete South Korean address, not just district/dong-level text.
                16. mapQuery must be optimized for Kakao Map search using the official place name plus dong/gu.
                17. If a requested category key is KARAOKE, return only real karaoke venues such as coin karaoke rooms. Do not replace it with cafes or generic activities.
                18. If a requested category key is SHOPPING, return only real shopping places such as malls, department stores, select shops, bookstores, markets, or shopping streets. Do not replace it with cafes or generic activities.
                19. Routes must reuse only venues already present in categories. Do not create new venues inside routes.
                20. All explanatory text should be natural Korean, concise, and free of awkward line breaks.
                """.formatted(
                keyword,
                valueOrDefault(meetingType, "미정"),
                valueOrDefault(mood, "미정"),
                valueOrDefault(area, keyword),
                valueOrDefault(planHint, "없음"),
                requestedCategorySummary,
                knownCandidates
        );
    }

    private String buildSelectedRoutePrompt(
            String keyword,
            String meetingType,
            String mood,
            String area,
            String planHint,
            List<KeywordRecommendResponse> selectedPlaces
    ) {
        String selectedSummary = selectedPlaces.stream()
                .map(place -> "- [%s] %s | 주소: %s | 검색어: %s".formatted(
                        categoryTitle(normalizeCategoryKey(place.getCategory())),
                        place.getName(),
                        place.getAddress(),
                        firstNonBlank(place.getMapQuery(), place.getName())
                ))
                .collect(Collectors.joining("\n"));

        return """
                User request:
                - Main keyword: %s
                - Meeting type: %s
                - Preferred mood: %s
                - Preferred area: %s
                - Desired flow / menu / exclusions: %s

                User-selected venues:
                %s

                Instructions:
                1. Use only the selected venues above. Never add a new venue.
                2. Reuse placeName, address, and mapQuery exactly as provided.
                3. Create 2 to 3 clearly different route concepts, not simple rotations.
                4. Each route should express a different strategy, such as easing into conversation, starting with an activity, or stretching the night longer.
                5. Each route should contain 2 to 3 steps and should not repeat the same place inside one route.
                6. title, summary, fitReason, and each step reason must be concise, natural Korean.
                7. Avoid awkward phrasing, repeated slogans, or generic filler sentences.
                """.formatted(
                valueOrDefault(keyword, area),
                valueOrDefault(meetingType, "미정"),
                valueOrDefault(mood, "미정"),
                valueOrDefault(area, keyword),
                valueOrDefault(planHint, "없음"),
                selectedSummary
        );
    }

    private KeywordRecommendResult normalizeKeywordResult(
            KeywordRecommendResult raw,
            String keyword,
            String meetingType,
            String mood,
            String area,
            List<String> requestedCategories
    ) {
        Map<String, List<KeywordRecommendResponse>> bucketedPlaces = new LinkedHashMap<>();
        Map<String, String> categoryDescriptions = new HashMap<>();
        requestedCategories.forEach(category -> bucketedPlaces.put(category, new ArrayList<>()));

        if (raw.getCategories() != null) {
            for (KeywordRecommendCategory category : raw.getCategories()) {
                String categoryKey = normalizeCategoryKey(firstNonBlank(category.getKey(), category.getTitle()));
                if (categoryDescriptions.get(categoryKey) == null && category.getDescription() != null && !category.getDescription().isBlank()) {
                    categoryDescriptions.put(categoryKey, sanitizeText(category.getDescription()));
                }
                if (category.getPlaces() == null) {
                    continue;
                }
                for (KeywordRecommendResponse place : category.getPlaces()) {
                    addPlace(bucketedPlaces, categoryKey, place, keyword, meetingType, mood, area);
                }
            }
        }

        if (raw.getRecommendations() != null) {
            for (KeywordRecommendResponse place : raw.getRecommendations()) {
                addPlace(bucketedPlaces, normalizeCategoryKey(place.getCategory()), place, keyword, meetingType, mood, area);
            }
        }

        List<KeywordRecommendCategory> categories = new ArrayList<>();
        for (String categoryKey : requestedCategories) {
            List<KeywordRecommendResponse> basePlaces = bucketedPlaces.getOrDefault(categoryKey, List.of());
            List<KeywordRecommendResponse> places = completeCategoryPlaces(
                    categoryKey,
                    basePlaces,
                    keyword,
                    meetingType,
                    mood,
                    area
            );

            categories.add(
                    KeywordRecommendCategory.builder()
                            .key(categoryKey)
                            .title(categoryTitle(categoryKey))
                            .description(firstNonBlank(categoryDescriptions.get(categoryKey), defaultCategoryDescription(categoryKey, meetingType, mood)))
                            .places(places)
                            .build()
            );
        }

        List<KeywordRecommendRoute> routes = normalizeRoutes(raw.getRoutes(), categories, keyword, meetingType, mood, area);

        return KeywordRecommendResult.builder()
                .categories(categories)
                .routes(routes)
                .recommendations(flattenRecommendations(categories))
                .build();
    }

    private void addPlace(
            Map<String, List<KeywordRecommendResponse>> bucketedPlaces,
            String categoryKey,
            KeywordRecommendResponse place,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        String normalizedCategory = bucketedPlaces.containsKey(categoryKey)
                ? categoryKey
                : inferCategoryKey(place.getName(), categoryKey);
        if (!bucketedPlaces.containsKey(normalizedCategory)) {
            return;
        }
        bucketedPlaces.get(normalizedCategory).add(
                normalizePlace(place, normalizedCategory, keyword, meetingType, mood, area)
        );
    }

    private KeywordRecommendResponse normalizePlace(
            KeywordRecommendResponse place,
            String categoryKey,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        String name = sanitizeText(valueOrDefault(place.getName(), categoryTitle(categoryKey) + " 추천"));
        String address = sanitizeText(firstNonBlank(place.getAddress(), buildAreaAddress(area, keyword)));
        String reason = sanitizeText(firstNonBlank(place.getReason(), defaultReason(categoryKey, meetingType, mood)));
        List<String> tags = normalizeTags(place.getTags(), categoryKey, mood);
        String mapQuery = sanitizeText(firstNonBlank(
                place.getMapQuery(),
                buildMapQuery(name, address, area, categoryKey)
        ));

        return KeywordRecommendResponse.builder()
                .category(categoryKey)
                .name(name)
                .address(address)
                .reason(reason)
                .tags(tags)
                .mapQuery(mapQuery)
                .build();
    }

    private List<KeywordRecommendResponse> completeCategoryPlaces(
            String categoryKey,
            List<KeywordRecommendResponse> basePlaces,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        List<KeywordRecommendResponse> merged = mergeUniquePlaces(basePlaces, fallbackPlaces(categoryKey, keyword, meetingType, mood, area));
        return merged.stream()
                .limit(CATEGORY_PLACE_TARGET)
                .map(place -> normalizePlace(place, categoryKey, keyword, meetingType, mood, area))
                .toList();
    }

    private List<KeywordRecommendRoute> normalizeRoutes(
            List<KeywordRecommendRoute> rawRoutes,
            List<KeywordRecommendCategory> categories,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        List<KeywordRecommendRoute> routes = new ArrayList<>();
        Map<String, KeywordRecommendResponse> placeLookup = buildPlaceLookup(categories);

        if (rawRoutes != null) {
            for (KeywordRecommendRoute route : rawRoutes) {
                if (route.getSteps() == null || route.getSteps().isEmpty()) {
                    continue;
                }

                List<KeywordRecommendRouteStep> normalizedSteps = new ArrayList<>();
                int order = 1;
                for (KeywordRecommendRouteStep step : route.getSteps()) {
                    KeywordRecommendResponse matchedPlace = findMatchingPlace(placeLookup, step.getPlaceName(), step.getAddress());
                    String categoryKey = matchedPlace == null
                            ? normalizeCategoryKey(step.getCategory())
                            : matchedPlace.getCategory();
                    String placeName = sanitizeText(firstNonBlank(step.getPlaceName(), matchedPlace == null ? null : matchedPlace.getName()));
                    String address = sanitizeText(firstNonBlank(step.getAddress(), matchedPlace == null ? null : matchedPlace.getAddress(), buildAreaAddress(area, keyword)));
                    String mapQuery = sanitizeText(firstNonBlank(step.getMapQuery(), matchedPlace == null ? null : matchedPlace.getMapQuery(), buildMapQuery(placeName, address, area, categoryKey)));

                    if (placeName == null || placeName.isBlank()) {
                        continue;
                    }

                    normalizedSteps.add(
                            KeywordRecommendRouteStep.builder()
                                    .order(step.getOrder() == null ? order : step.getOrder())
                                    .category(categoryKey)
                                    .placeName(placeName)
                                    .address(address)
                                    .reason(sanitizeText(firstNonBlank(step.getReason(), defaultStepReason(categoryKey, meetingType, mood))))
                                    .mapQuery(mapQuery)
                                    .build()
                    );
                    order += 1;
                }

                if (!normalizedSteps.isEmpty()) {
                    routes.add(
                            KeywordRecommendRoute.builder()
                                    .title(sanitizeText(firstNonBlank(route.getTitle(), "추천 코스 " + (routes.size() + 1))))
                                    .summary(sanitizeText(firstNonBlank(route.getSummary(), defaultRouteSummary(meetingType, mood))))
                                    .fitReason(sanitizeText(firstNonBlank(route.getFitReason(), defaultRouteFitReason(meetingType, mood))))
                                    .steps(normalizedSteps)
                                    .build()
                    );
                }
            }
        }

        List<KeywordRecommendRoute> fallbackRoutes = buildFallbackRoutes(categories, meetingType, mood);
        for (KeywordRecommendRoute route : fallbackRoutes) {
            if (routes.size() >= 3) {
                break;
            }
            routes.add(route);
        }

        return routes.stream().limit(3).toList();
    }

    private List<KeywordRecommendRoute> normalizeSelectedRoutes(
            List<KeywordRecommendRoute> rawRoutes,
            List<KeywordRecommendResponse> selectedPlaces,
            String meetingType,
            String mood,
            String area,
            String keyword
    ) {
        List<KeywordRecommendRoute> routes = new ArrayList<>();
        Map<String, KeywordRecommendResponse> placeLookup = buildPlaceLookupFromPlaces(selectedPlaces);

        if (rawRoutes != null) {
            for (KeywordRecommendRoute route : rawRoutes) {
                if (route.getSteps() == null || route.getSteps().isEmpty()) {
                    continue;
                }

                List<KeywordRecommendRouteStep> normalizedSteps = new ArrayList<>();
                int order = 1;
                for (KeywordRecommendRouteStep step : route.getSteps()) {
                    KeywordRecommendResponse matchedPlace = findMatchingPlace(
                            placeLookup,
                            step.getPlaceName(),
                            step.getAddress()
                    );
                    if (matchedPlace == null) {
                        continue;
                    }

                    normalizedSteps.add(
                            KeywordRecommendRouteStep.builder()
                                    .order(step.getOrder() == null ? order : step.getOrder())
                                    .category(matchedPlace.getCategory())
                                    .placeName(matchedPlace.getName())
                                    .address(sanitizeText(firstNonBlank(matchedPlace.getAddress(), buildAreaAddress(area, keyword))))
                                    .reason(firstNonBlank(
                                            step.getReason(),
                                            matchedPlace.getReason(),
                                            defaultStepReason(matchedPlace.getCategory(), meetingType, mood)
                                    ))
                                    .mapQuery(firstNonBlank(
                                            matchedPlace.getMapQuery(),
                                            buildMapQuery(matchedPlace.getName(), matchedPlace.getAddress(), area, matchedPlace.getCategory())
                                    ))
                                    .build()
                    );
                    order += 1;
                }

                if (normalizedSteps.size() >= 2) {
                    routes.add(
                            KeywordRecommendRoute.builder()
                                    .title(sanitizeText(firstNonBlank(route.getTitle(), "선택 장소 코스 " + (routes.size() + 1))))
                                    .summary(sanitizeText(firstNonBlank(route.getSummary(), defaultRouteSummary(meetingType, mood))))
                                    .fitReason(sanitizeText(firstNonBlank(route.getFitReason(), defaultRouteFitReason(meetingType, mood))))
                                    .steps(normalizedSteps)
                                    .build()
                    );
                }
            }
        }

        for (KeywordRecommendRoute fallbackRoute : buildSelectedPlaceRoutes(selectedPlaces, meetingType, mood)) {
            if (routes.size() >= 3) {
                break;
            }

            boolean duplicated = routes.stream()
                    .anyMatch(existing -> existing.getTitle().equals(fallbackRoute.getTitle()));
            if (!duplicated) {
                routes.add(fallbackRoute);
            }
        }

        return routes.stream().limit(3).toList();
    }

    private KeywordRecommendResult buildDbKeywordResult(
            List<PlaceCandidate> dbMatches,
            String keyword,
            String meetingType,
            String mood,
            String area,
            List<String> requestedCategories
    ) {
        Map<String, List<KeywordRecommendResponse>> bucketed = new LinkedHashMap<>();
        requestedCategories.forEach(category -> bucketed.put(category, new ArrayList<>()));

        for (PlaceCandidate place : dbMatches) {
            String categoryKey = normalizeCategoryKey(place.getCategory());
            if (!bucketed.containsKey(categoryKey)) {
                categoryKey = inferCategoryKey(place.getName(), place.getCategory());
            }
            if (!bucketed.containsKey(categoryKey)) {
                continue;
            }

            bucketed.get(categoryKey).add(
                    KeywordRecommendResponse.builder()
                            .category(categoryKey)
                            .name(place.getName())
                            .address(buildAreaAddress(area, keyword))
                            .reason("내부 후보 데이터에서 찾은 장소입니다.")
                            .tags(defaultTags(categoryKey, mood))
                            .mapQuery(buildMapQuery(place.getName(), buildAreaAddress(area, keyword), area, categoryKey))
                            .build()
            );
        }

        List<KeywordRecommendCategory> categories = requestedCategories.stream()
                .map(categoryKey -> KeywordRecommendCategory.builder()
                        .key(categoryKey)
                        .title(categoryTitle(categoryKey))
                        .description(defaultCategoryDescription(categoryKey, meetingType, mood))
                        .places(completeCategoryPlaces(categoryKey, bucketed.get(categoryKey), keyword, meetingType, mood, area))
                        .build())
                .toList();

        return KeywordRecommendResult.builder()
                .categories(categories)
                .routes(buildFallbackRoutes(categories, meetingType, mood))
                .recommendations(flattenRecommendations(categories))
                .build();
    }

    private KeywordRecommendResult buildFallbackKeywordResult(
            String keyword,
            String meetingType,
            String mood,
            String area,
            List<String> requestedCategories
    ) {
        List<KeywordRecommendCategory> categories = requestedCategories.stream()
                .map(categoryKey -> KeywordRecommendCategory.builder()
                        .key(categoryKey)
                        .title(categoryTitle(categoryKey))
                        .description(defaultCategoryDescription(categoryKey, meetingType, mood))
                        .places(completeCategoryPlaces(categoryKey, fallbackPlaces(categoryKey, keyword, meetingType, mood, area), keyword, meetingType, mood, area))
                        .build())
                .toList();

        return KeywordRecommendResult.builder()
                .categories(categories)
                .routes(buildFallbackRoutes(categories, meetingType, mood))
                .recommendations(flattenRecommendations(categories))
                .build();
    }

    private List<KeywordRecommendRoute> buildFallbackRoutes(
            List<KeywordRecommendCategory> categories,
            String meetingType,
            String mood
    ) {
        List<KeywordRecommendResponse> orderedPlaces = categories.stream()
                .map(category -> category.getPlaces() == null || category.getPlaces().isEmpty() ? null : category.getPlaces().get(0))
                .filter(place -> place != null)
                .limit(4)
                .toList();

        if (orderedPlaces.size() < 2) {
            return List.of();
        }

        List<List<KeywordRecommendResponse>> rotations = List.of(
                orderedPlaces,
                rotatePlaces(orderedPlaces, 1),
                rotatePlaces(orderedPlaces, Math.min(2, Math.max(orderedPlaces.size() - 1, 1)))
        );

        List<KeywordRecommendRoute> routes = new ArrayList<>();
        List<String> titles = List.of("기본 이동 코스", "가볍게 풀리는 코스", "길게 이어지는 코스");
        List<String> summaries = List.of(
                "선택한 흐름에 맞춰 장소를 자연스럽게 이어본 기본 코스입니다.",
                "분위기를 천천히 올리며 이동하기 좋은 순서로 재배치한 코스입니다.",
                "오래 머물며 여러 포인트를 챙기기 좋은 반나절 코스입니다."
        );

        for (int index = 0; index < rotations.size(); index++) {
            List<KeywordRecommendResponse> places = rotations.get(index);
            if (places.size() < 2) {
                continue;
            }

            routes.add(
                    KeywordRecommendRoute.builder()
                            .title(titles.get(index))
                            .summary(summaries.get(index))
                            .fitReason(defaultRouteFitReason(meetingType, mood))
                            .steps(buildStepsFromPlaces(places, meetingType, mood))
                            .build()
            );
        }

        return routes;
    }

    private List<KeywordRecommendRoute> buildSelectedPlaceRoutes(
            List<KeywordRecommendResponse> selectedPlaces,
            String meetingType,
            String mood
    ) {
        if (selectedPlaces.size() < 2) {
            return List.of();
        }

        List<RouteScenario> scenarios = List.of(
                new RouteScenario(
                        "대화가 자연스럽게 풀리는 코스",
                        "식사로 시작해 활동 포인트를 한 번 넣고 대화가 이어질 자리에 마무리하는 흐름입니다.",
                        buildContextSummary(meetingType, mood) + "에서 처음 어색함을 줄이고 싶을 때 잘 맞습니다.",
                        List.of("RESTAURANT", "ACTIVITY", "SHOPPING", "PC_ROOM", "CAFE", "BAR", "KARAOKE")
                ),
                new RouteScenario(
                        "바로 분위기를 올리는 코스",
                        "초반에 놀거리나 몰입 포인트를 먼저 두고 뒤에서 식사와 대화를 붙이는 흐름입니다.",
                        "정적인 시작보다 바로 같이 무언가 하면서 가까워지고 싶을 때 어울립니다.",
                        List.of("ACTIVITY", "SHOPPING", "PC_ROOM", "RESTAURANT", "CAFE", "BAR", "KARAOKE")
                ),
                new RouteScenario(
                        "밤까지 길게 이어지는 코스",
                        "식사 후 카페나 술집으로 여운을 끌고 가면서 천천히 시간을 늘리는 흐름입니다.",
                        "조금 오래 놀거나 2차까지 자연스럽게 이어가고 싶을 때 참고하기 좋습니다.",
                        List.of("RESTAURANT", "CAFE", "BAR", "KARAOKE", "SHOPPING", "ACTIVITY", "PC_ROOM")
                )
        );

        List<KeywordRecommendRoute> routes = new ArrayList<>();
        LinkedHashSet<String> seenSignatures = new LinkedHashSet<>();

        for (RouteScenario scenario : scenarios) {
            List<KeywordRecommendResponse> orderedPlaces = orderPlacesByScenario(selectedPlaces, scenario.categoryOrder());
            if (orderedPlaces.size() < 2) {
                continue;
            }

            String signature = orderedPlaces.stream()
                    .map(place -> place.getName() + "|" + place.getAddress())
                    .collect(Collectors.joining(" > "));
            if (!seenSignatures.add(signature)) {
                continue;
            }

            routes.add(
                    KeywordRecommendRoute.builder()
                            .title(scenario.title())
                            .summary(scenario.summary())
                            .fitReason(scenario.fitReason())
                            .steps(buildStepsFromPlaces(orderedPlaces, meetingType, mood))
                            .build()
            );
        }

        return routes.stream().limit(3).toList();
    }

    private KeywordRecommendRouteStep toRouteStep(KeywordRecommendResponse place, int order, String reason) {
        return KeywordRecommendRouteStep.builder()
                .order(order)
                .category(place.getCategory())
                .placeName(place.getName())
                .address(place.getAddress())
                .reason(reason)
                .mapQuery(place.getMapQuery())
                .build();
    }

    private List<KeywordRecommendRouteStep> buildStepsFromPlaces(
            List<KeywordRecommendResponse> places,
            String meetingType,
            String mood
    ) {
        List<KeywordRecommendRouteStep> steps = new ArrayList<>();
        int order = 1;
        for (KeywordRecommendResponse place : places) {
            steps.add(
                    KeywordRecommendRouteStep.builder()
                            .order(order)
                            .category(place.getCategory())
                            .placeName(place.getName())
                            .address(place.getAddress())
                            .reason(firstNonBlank(place.getReason(), defaultStepReason(place.getCategory(), meetingType, mood)))
                            .mapQuery(place.getMapQuery())
                            .build()
            );
            order += 1;
        }
        return steps;
    }

    private List<KeywordRecommendResponse> rotatePlaces(List<KeywordRecommendResponse> places, int offset) {
        if (places.isEmpty()) {
            return List.of();
        }

        int normalizedOffset = Math.floorMod(offset, places.size());
        List<KeywordRecommendResponse> rotated = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            rotated.add(places.get((index + normalizedOffset) % places.size()));
        }
        return rotated;
    }

    private List<KeywordRecommendResponse> orderPlacesByScenario(
            List<KeywordRecommendResponse> selectedPlaces,
            List<String> categoryOrder
    ) {
        List<KeywordRecommendResponse> ordered = new ArrayList<>();
        LinkedHashSet<String> used = new LinkedHashSet<>();

        for (String categoryKey : categoryOrder) {
            for (KeywordRecommendResponse place : selectedPlaces) {
                if (!categoryKey.equals(normalizeCategoryKey(place.getCategory()))) {
                    continue;
                }

                String signature = place.getName() + "|" + place.getAddress();
                if (used.add(signature)) {
                    ordered.add(place);
                }
            }
        }

        for (KeywordRecommendResponse place : selectedPlaces) {
            String signature = place.getName() + "|" + place.getAddress();
            if (used.add(signature)) {
                ordered.add(place);
            }
        }

        return ordered;
    }

    private KeywordRecommendResponse placeAt(List<KeywordRecommendCategory> categories, String categoryKey, int index) {
        for (KeywordRecommendCategory category : categories) {
            if (categoryKey.equals(category.getKey()) && category.getPlaces() != null && category.getPlaces().size() > index) {
                return category.getPlaces().get(index);
            }
        }
        return fallbackPlaces(categoryKey, categoryTitle(categoryKey), "모임", "편안한", categoryTitle(categoryKey)).get(Math.min(index, CATEGORY_PLACE_TARGET - 1));
    }

    private Map<String, KeywordRecommendResponse> buildPlaceLookup(List<KeywordRecommendCategory> categories) {
        Map<String, KeywordRecommendResponse> lookup = new LinkedHashMap<>();
        for (KeywordRecommendCategory category : categories) {
            if (category.getPlaces() == null) {
                continue;
            }
            for (KeywordRecommendResponse place : category.getPlaces()) {
                lookup.put(place.getName(), place);
                lookup.put(place.getName() + "|" + place.getAddress(), place);
            }
        }
        return lookup;
    }

    private Map<String, KeywordRecommendResponse> buildPlaceLookupFromPlaces(List<KeywordRecommendResponse> places) {
        Map<String, KeywordRecommendResponse> lookup = new LinkedHashMap<>();
        for (KeywordRecommendResponse place : places) {
            lookup.put(place.getName(), place);
            lookup.put(place.getName() + "|" + place.getAddress(), place);
        }
        return lookup;
    }

    private KeywordRecommendResponse findMatchingPlace(
            Map<String, KeywordRecommendResponse> placeLookup,
            String placeName,
            String address
    ) {
        if (placeName != null && !placeName.isBlank()) {
            KeywordRecommendResponse byNameAndAddress = placeLookup.get(placeName + "|" + valueOrDefault(address, ""));
            if (byNameAndAddress != null) {
                return byNameAndAddress;
            }

            KeywordRecommendResponse byName = placeLookup.get(placeName);
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    private List<KeywordRecommendResponse> flattenRecommendations(List<KeywordRecommendCategory> categories) {
        Map<String, KeywordRecommendResponse> deduplicated = new LinkedHashMap<>();
        for (KeywordRecommendCategory category : categories) {
            if (category.getPlaces() == null) {
                continue;
            }
            for (KeywordRecommendResponse place : category.getPlaces()) {
                deduplicated.putIfAbsent(place.getName() + "|" + place.getAddress(), place);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<KeywordRecommendResponse> normalizeSelectedPlaces(
            List<SelectedRouteRecommendRequest.SelectedPlace> rawPlaces,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        if (rawPlaces == null) {
            return List.of();
        }

        Map<String, KeywordRecommendResponse> normalized = new LinkedHashMap<>();
        for (SelectedRouteRecommendRequest.SelectedPlace rawPlace : rawPlaces) {
            String categoryKey = normalizeCategoryKey(rawPlace.getCategory());
            if (normalized.containsKey(categoryKey)) {
                continue;
            }

            KeywordRecommendResponse place = normalizePlace(
                    KeywordRecommendResponse.builder()
                            .category(categoryKey)
                            .name(rawPlace.getName())
                            .address(rawPlace.getAddress())
                            .reason(rawPlace.getReason())
                            .tags(rawPlace.getTags())
                            .mapQuery(rawPlace.getMapQuery())
                            .build(),
                    categoryKey,
                    keyword,
                    meetingType,
                    mood,
                    area
            );
            normalized.put(categoryKey, place);
        }

        return new ArrayList<>(normalized.values());
    }

    private List<KeywordRecommendResponse> mergeUniquePlaces(
            List<KeywordRecommendResponse> primary,
            List<KeywordRecommendResponse> secondary
    ) {
        Map<String, KeywordRecommendResponse> deduplicated = new LinkedHashMap<>();
        for (KeywordRecommendResponse place : primary) {
            deduplicated.putIfAbsent(place.getName() + "|" + place.getAddress(), place);
        }
        for (KeywordRecommendResponse place : secondary) {
            deduplicated.putIfAbsent(place.getName() + "|" + place.getAddress(), place);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<KeywordRecommendResponse> fallbackPlaces(
            String categoryKey,
            String keyword,
            String meetingType,
            String mood,
            String area
    ) {
        String baseArea = firstNonBlank(area, keyword, "서울");
        String areaAddress = buildAreaAddress(baseArea, keyword);

        if ("RESTAURANT".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " 한식당", areaAddress, defaultReason(categoryKey, meetingType, mood), List.of("#식사하기좋음", "#대화시작", tagFromMood(mood)), baseArea + " 한식 맛집"),
                    buildFallbackPlace(categoryKey, baseArea + " 파스타 바", areaAddress, "분위기와 식사 만족도를 함께 챙기기 좋은 식당입니다.", List.of("#분위기좋음", "#데이트식사", "#예약추천"), baseArea + " 파스타 맛집"),
                    buildFallbackPlace(categoryKey, baseArea + " 고깃집", areaAddress, "활기 있게 식사하며 분위기를 풀기 좋은 장소입니다.", List.of("#고기맛집", "#모임추천", "#저녁코스"), baseArea + " 고깃집"),
                    buildFallbackPlace(categoryKey, baseArea + " 스테이크 하우스", areaAddress, "분위기를 조금 더 살리고 싶을 때 고르기 좋은 식당입니다.", List.of("#스테이크", "#분위기저녁", "#기념일식사"), baseArea + " 스테이크"),
                    buildFallbackPlace(categoryKey, baseArea + " 숙성 삼겹살", areaAddress, "친구나 연인과 저녁 메뉴로 실패 확률이 낮은 고깃집 후보입니다.", List.of("#삼겹살", "#저녁맛집", "#모임식사"), baseArea + " 삼겹살")
            );
        }

        if ("CAFE".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " 로스터리 카페", areaAddress, "식사 후 오래 머물며 대화하기 좋은 카페입니다.", List.of("#대화하기좋음", "#분위기좋음", tagFromMood(mood)), baseArea + " 분위기 좋은 카페"),
                    buildFallbackPlace(categoryKey, baseArea + " 디저트 카페", areaAddress, "디저트와 함께 가볍게 쉬어가기 좋은 장소입니다.", List.of("#디저트맛집", "#휴식", "#사진찍기좋음"), baseArea + " 디저트 카페"),
                    buildFallbackPlace(categoryKey, baseArea + " 북카페", areaAddress, "조용히 앉아 이야기 나누기 좋은 카페입니다.", List.of("#조용한공간", "#책읽기좋음", "#여유로운"), baseArea + " 북카페"),
                    buildFallbackPlace(categoryKey, baseArea + " 케이크 카페", areaAddress, "케이크나 구움과자 쪽이 강한 디저트 카페 후보입니다.", List.of("#케이크맛집", "#디저트강함", "#카페후보"), baseArea + " 케이크 카페"),
                    buildFallbackPlace(categoryKey, baseArea + " 브런치 카페", areaAddress, "식사와 카페의 중간 느낌으로 오래 머물기 좋은 장소입니다.", List.of("#브런치", "#오래앉기좋음", "#대화하기좋음"), baseArea + " 브런치 카페")
            );
        }

        if ("BAR".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " 와인 바", areaAddress, "식사 뒤 분위기를 이어가기 좋은 술집입니다.", List.of("#2차추천", "#분위기좋음", tagFromMood(mood)), baseArea + " 와인바"),
                    buildFallbackPlace(categoryKey, baseArea + " 하이볼 바", areaAddress, "가볍게 한 잔하며 이야기 이어가기 좋은 장소입니다.", List.of("#가볍게한잔", "#대화하기좋음", "#캐주얼술집"), baseArea + " 하이볼 바"),
                    buildFallbackPlace(categoryKey, baseArea + " 이자카야", areaAddress, "늦은 시간까지 머물며 술자리를 이어가기 좋습니다.", List.of("#늦게까지", "#안주좋음", "#2차코스"), baseArea + " 이자카야"),
                    buildFallbackPlace(categoryKey, baseArea + " 조용한 바", areaAddress, "시끄럽지 않게 2차로 이어가기 좋은 술집 후보입니다.", List.of("#조용한술집", "#대화하기좋음", "#2차추천"), baseArea + " 조용한 바"),
                    buildFallbackPlace(categoryKey, baseArea + " 펍", areaAddress, "조금 더 캐주얼하게 2차 분위기를 만들기 좋은 술집입니다.", List.of("#캐주얼술집", "#맥주", "#가볍게한잔"), baseArea + " 펍")
            );
        }

        if ("PC_ROOM".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " PC방", areaAddress, "카페 대신 바로 게임이나 온라인 활동을 즐기기 좋은 장소입니다.", List.of("#게임하기좋음", "#카페대신", tagFromMood(mood)), baseArea + " PC방"),
                    buildFallbackPlace(categoryKey, baseArea + " 프리미엄 PC 라운지", areaAddress, "오래 머물며 같이 게임하기 편한 환경입니다.", List.of("#장비좋음", "#오래머물기좋음", "#친구모임"), baseArea + " 프리미엄 PC방"),
                    buildFallbackPlace(categoryKey, baseArea + " 게임 라운지", areaAddress, "함께 시간을 보내기 쉬운 실내형 게임 장소입니다.", List.of("#실내활동", "#친구들끼리", "#가볍게놀기"), baseArea + " 게임 라운지"),
                    buildFallbackPlace(categoryKey, baseArea + " e스포츠 PC방", areaAddress, "사양이나 좌석 환경을 조금 더 챙기고 싶을 때 고르기 좋습니다.", List.of("#장비좋음", "#같이놀기좋음", "#장시간이용"), baseArea + " e스포츠 PC방"),
                    buildFallbackPlace(categoryKey, baseArea + " 게임카페", areaAddress, "게임과 대화를 함께 섞어가기 좋은 실내형 후보입니다.", List.of("#친구모임", "#실내동선", "#게임후보"), baseArea + " 게임카페")
            );
        }

        if ("KARAOKE".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " 코인노래방", areaAddress, "마지막에 가볍게 들러 분위기를 마무리하기 좋은 노래방입니다.", List.of("#코인노래방", "#마무리코스", "#친구모임"), baseArea + " 코인노래방"),
                    buildFallbackPlace(categoryKey, baseArea + " 노래연습장", areaAddress, "식사나 술자리 뒤에 이어가기 쉬운 노래방 후보입니다.", List.of("#노래방", "#2차마무리", "#같이가기좋음"), baseArea + " 노래방"),
                    buildFallbackPlace(categoryKey, baseArea + " 코노", areaAddress, "짧게 들러 텐션을 올리기 좋은 코인노래방 후보입니다.", List.of("#코노", "#가볍게놀기", "#야간코스"), baseArea + " 코인노래방"),
                    buildFallbackPlace(categoryKey, baseArea + " 럭셔리 수 노래연습장", areaAddress, "단체로 들러도 공간이 무난한 노래방 후보입니다.", List.of("#단체노래방", "#심야코스", "#회식후"), baseArea + " 수 노래연습장"),
                    buildFallbackPlace(categoryKey, baseArea + " 락휴 코인노래방", areaAddress, "마지막 코스로 가볍게 부르기 좋은 코인노래방 후보입니다.", List.of("#락휴", "#코인노래방", "#가볍게마무리"), baseArea + " 락휴 코인노래방")
            );
        }

        if ("SHOPPING".equals(categoryKey)) {
            return List.of(
                    buildFallbackPlace(categoryKey, baseArea + " 편집샵", areaAddress, "같이 둘러보며 취향을 나누기 좋은 쇼핑 후보입니다.", List.of("#편집샵", "#구경하기좋음", "#취향공유"), baseArea + " 편집샵"),
                    buildFallbackPlace(categoryKey, baseArea + " 쇼핑몰", areaAddress, "날씨와 상관없이 한 번에 둘러보기 좋은 쇼핑 후보입니다.", List.of("#쇼핑몰", "#실내쇼핑", "#동선편함"), baseArea + " 쇼핑몰"),
                    buildFallbackPlace(categoryKey, baseArea + " 백화점", areaAddress, "식사와 카페까지 한 번에 엮기 쉬운 대표 쇼핑 후보입니다.", List.of("#백화점", "#브랜드많음", "#카페연결"), baseArea + " 백화점"),
                    buildFallbackPlace(categoryKey, baseArea + " 소품샵", areaAddress, "짧게 들러도 재미가 있는 감도 높은 쇼핑 후보입니다.", List.of("#소품샵", "#감성쇼핑", "#사진찍기좋음"), baseArea + " 소품샵"),
                    buildFallbackPlace(categoryKey, baseArea + " 서점", areaAddress, "조용히 둘러보며 자연스럽게 대화하기 좋은 공간입니다.", List.of("#서점", "#조용한쇼핑", "#산책동선"), baseArea + " 서점")
            );
        }

        return List.of(
                buildFallbackPlace(categoryKey, baseArea + " 소품샵 거리", areaAddress, "가볍게 걸으며 함께 구경하기 좋은 동선입니다.", List.of("#산책하기좋음", "#구경거리", tagFromMood(mood)), baseArea + " 소품샵"),
                buildFallbackPlace(categoryKey, baseArea + " 전시 공간", areaAddress, "대화 소재가 생기고 사진도 남기기 좋은 장소입니다.", List.of("#전시추천", "#사진찍기좋음", "#실내데이트"), baseArea + " 전시"),
                buildFallbackPlace(categoryKey, baseArea + " 보드게임 카페", areaAddress, "자연스럽게 친해지기 좋은 체험형 장소입니다.", List.of("#같이놀기좋음", "#실내활동", "#어색함해소"), baseArea + " 보드게임 카페"),
                buildFallbackPlace(categoryKey, baseArea + " 산책길", areaAddress, "짧게 걷거나 구경하며 흐름을 전환하기 좋은 장소입니다.", List.of("#산책", "#분위기전환", "#걷기좋음"), baseArea + " 산책"),
                buildFallbackPlace(categoryKey, baseArea + " 전시관", areaAddress, "실내에서 대화 소재를 만들기 좋은 후보입니다.", List.of("#전시", "#실내활동", "#함께보기좋음"), baseArea + " 전시관")
        );
    }

    private KeywordRecommendResponse buildFallbackPlace(
            String categoryKey,
            String name,
            String address,
            String reason,
            List<String> tags,
            String mapQuery
    ) {
        return KeywordRecommendResponse.builder()
                .category(categoryKey)
                .name(name)
                .address(address)
                .reason(reason)
                .tags(normalizeTags(tags, categoryKey, null))
                .mapQuery(mapQuery)
                .build();
    }

    private List<String> normalizeTags(List<String> tags, String categoryKey, String mood) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String sanitizedTag = sanitizeText(tag);
                normalized.add(sanitizedTag.startsWith("#") ? sanitizedTag : "#" + sanitizedTag);
            }
        }

        if (normalized.isEmpty()) {
            normalized.addAll(defaultTags(categoryKey, mood));
        }

        return normalized.stream().limit(4).toList();
    }

    private List<String> defaultTags(String categoryKey, String mood) {
        List<String> tags = new ArrayList<>();
        if ("RESTAURANT".equals(categoryKey)) {
            tags.add("#식사하기좋음");
            tags.add("#대화시작");
        } else if ("CAFE".equals(categoryKey)) {
            tags.add("#대화하기좋음");
            tags.add("#분위기좋음");
        } else if ("BAR".equals(categoryKey)) {
            tags.add("#가볍게한잔");
            tags.add("#2차추천");
        } else if ("PC_ROOM".equals(categoryKey)) {
            tags.add("#게임하기좋음");
            tags.add("#실내활동");
        } else if ("KARAOKE".equals(categoryKey)) {
            tags.add("#코인노래방");
            tags.add("#마무리코스");
        } else if ("SHOPPING".equals(categoryKey)) {
            tags.add("#쇼핑하기좋음");
            tags.add("#구경거리");
        } else {
            tags.add("#같이놀기좋음");
            tags.add("#구경거리");
        }
        tags.add(tagFromMood(mood));
        return tags;
    }

    private String tagFromMood(String mood) {
        String normalizedMood = valueOrDefault(mood, "편안한");
        return "#" + normalizedMood.replace(" ", "");
    }

    private String defaultReason(String categoryKey, String meetingType, String mood) {
        String summary = buildContextSummary(meetingType, mood);
        if ("RESTAURANT".equals(categoryKey)) {
            return summary + " 분위기에서 첫 식사와 대화를 시작하기 좋은 장소입니다.";
        }
        if ("CAFE".equals(categoryKey)) {
            return summary + " 흐름에서 오래 머물며 이야기 나누기 좋은 카페입니다.";
        }
        if ("BAR".equals(categoryKey)) {
            return summary + " 흐름에서 술자리나 2차를 이어가기 좋은 장소입니다.";
        }
        if ("PC_ROOM".equals(categoryKey)) {
            return summary + " 조건에서 카페 대신 바로 함께 시간을 보내기 좋은 PC방입니다.";
        }
        if ("KARAOKE".equals(categoryKey)) {
            return summary + " 흐름에서 마지막에 텐션을 올리거나 마무리하기 좋은 코인노래방입니다.";
        }
        if ("SHOPPING".equals(categoryKey)) {
            return summary + " 흐름에서 같이 둘러보고 취향을 나누기 좋은 쇼핑 장소입니다.";
        }
        return summary + " 모임에 활동 포인트를 더해주는 놀거리 장소입니다.";
    }

    private String defaultStepReason(String categoryKey, String meetingType, String mood) {
        return defaultReason(categoryKey, meetingType, mood);
    }

    private String defaultCategoryDescription(String categoryKey, String meetingType, String mood) {
        String summary = buildContextSummary(meetingType, mood);
        if ("RESTAURANT".equals(categoryKey)) {
            return summary + " 조건에서 식사 시작점을 고르기 좋은 후보들입니다.";
        }
        if ("CAFE".equals(categoryKey)) {
            return summary + " 조건에서 중간에 쉬어가며 대화하기 좋은 후보들입니다.";
        }
        if ("BAR".equals(categoryKey)) {
            return summary + " 조건에서 저녁 이후 술자리 흐름을 이어가기 좋은 후보들입니다.";
        }
        if ("PC_ROOM".equals(categoryKey)) {
            return summary + " 조건에서 카페 대신 오래 머물며 같이 놀기 좋은 후보들입니다.";
        }
        if ("KARAOKE".equals(categoryKey)) {
            return summary + " 조건에서 마지막 코스로 넣기 좋은 코인노래방 후보들입니다.";
        }
        if ("SHOPPING".equals(categoryKey)) {
            return summary + " 조건에서 둘러보기와 쇼핑 동선을 묶기 좋은 후보들입니다.";
        }
        return summary + " 조건에서 함께 시간을 보내기 좋은 놀거리 후보들입니다.";
    }

    private String defaultRouteSummary(String meetingType, String mood) {
        return buildContextSummary(meetingType, mood) + " 흐름에 맞춰 장소를 자연스럽게 이어본 코스입니다.";
    }

    private String defaultRouteFitReason(String meetingType, String mood) {
        return buildContextSummary(meetingType, mood) + " 분위기를 자연스럽게 만들고 싶은 모임에 잘 맞습니다.";
    }

    private String categoryTitle(String categoryKey) {
        return CATEGORY_TITLES.getOrDefault(categoryKey, "추천");
    }

    private String normalizeCategoryKey(String category) {
        if (category == null) {
            return "ACTIVITY";
        }

        String normalized = category.trim().toUpperCase();
        return switch (normalized) {
            case "RESTAURANT", "FOOD", "MEAL", "식당", "음식점", "맛집" -> "RESTAURANT";
            case "CAFE", "COFFEE", "카페", "디저트" -> "CAFE";
            case "BAR", "PUB", "DRINK", "술집", "바", "이자카야", "포차", "주점" -> "BAR";
            case "PC_ROOM", "PC", "PCROOM", "PC방", "피시방", "게임방" -> "PC_ROOM";
            case "KARAOKE", "KTV", "노래방", "코노", "코인노래방", "노래연습장" -> "KARAOKE";
            case "SHOPPING", "SHOP", "쇼핑", "쇼핑몰", "백화점", "편집샵", "서점", "아울렛", "소품샵" -> "SHOPPING";
            case "ACTIVITY", "PLAY", "FUN", "놀거리", "액티비티", "체험", "관광", "여행", "산책" -> "ACTIVITY";
            default -> "ACTIVITY";
        };
    }

    private String inferCategoryKey(String name, String fallbackCategory) {
        String normalizedName = valueOrDefault(name, "").toLowerCase();
        if (normalizedName.contains("카페") || normalizedName.contains("coffee") || normalizedName.contains("디저트")) {
            return "CAFE";
        }
        if (normalizedName.contains("이자카야") || normalizedName.contains("주점") || normalizedName.contains("포차") || normalizedName.contains("와인") || normalizedName.contains("하이볼") || normalizedName.contains("bar")) {
            return "BAR";
        }
        if (normalizedName.contains("pc") || normalizedName.contains("피시") || normalizedName.contains("게임")) {
            return "PC_ROOM";
        }
        if (normalizedName.contains("노래방") || normalizedName.contains("코노") || normalizedName.contains("코인")) {
            return "KARAOKE";
        }
        if (normalizedName.contains("백화점") || normalizedName.contains("쇼핑몰") || normalizedName.contains("편집샵") || normalizedName.contains("아울렛") || normalizedName.contains("서점") || normalizedName.contains("소품샵")) {
            return "SHOPPING";
        }
        if (normalizedName.contains("전시") || normalizedName.contains("보드게임") || normalizedName.contains("소품샵") || normalizedName.contains("공방")) {
            return "ACTIVITY";
        }
        if (normalizedName.contains("식당") || normalizedName.contains("파스타") || normalizedName.contains("고기") || normalizedName.contains("다이닝")) {
            return "RESTAURANT";
        }
        return normalizeCategoryKey(fallbackCategory);
    }

    private String buildMapQuery(String name, String address, String area, String categoryKey) {
        String queryBase = firstNonBlank(name, address, area, categoryTitle(categoryKey));
        String queryArea = firstNonBlank(area, address, "");
        if (queryArea.isBlank()) {
            return queryBase;
        }
        return (queryBase + " " + queryArea).trim();
    }

    private String buildAreaAddress(String area, String keyword) {
        return firstNonBlank(area, keyword, "서울");
    }

    private String buildContextSummary(String meetingType, String mood) {
        List<String> parts = new ArrayList<>();
        if (meetingType != null && !meetingType.isBlank()) {
            parts.add(meetingType.trim());
        }
        if (mood != null && !mood.isBlank()) {
            parts.add(mood.trim());
        }
        return parts.isEmpty() ? "입력한 조건" : String.join(" / ", parts);
    }

    private List<String> resolveRequestedCategories(String planHint) {
        String normalizedHint = normalizeSearchHint(planHint);
        String mainRequestHint = normalizeSearchHint(extractLabeledHint(planHint, "메인 요청"));
        String extraRequestHint = normalizeSearchHint(firstNonBlank(
                extractLabeledHint(planHint, "추가 요청"),
                extractLabeledHint(planHint, "추가로 찾고 싶은 것")
        ));
        String flowHint = normalizeSearchHint(extractLabeledHint(planHint, "원하는 흐름"));
        String restaurantHint = normalizeSearchHint(extractLabeledHint(planHint, "식당 취향"));
        String cafeHint = normalizeSearchHint(extractLabeledHint(planHint, "카페 취향"));
        String avoidHint = normalizeSearchHint(extractLabeledHint(planHint, "피하고 싶은 것"));

        String orderSource = firstNonBlank(flowHint, mainRequestHint, normalizedHint);
        String categorySource = firstNonBlank(mainRequestHint, normalizedHint) + extraRequestHint;
        LinkedHashSet<String> resolved = new LinkedHashSet<>();

        List<CategoryPosition> positioned = new ArrayList<>();
        addCategoryPosition(positioned, orderSource, "PC_ROOM", "pc방", "피시방", "게임방", "게임하");
        addCategoryPosition(positioned, orderSource, "RESTAURANT", "삼겹살", "고깃집", "저녁먹", "점심먹", "식사", "밥", "파스타", "양식", "한식", "중식", "일식");
        addCategoryPosition(positioned, orderSource, "CAFE", "카페", "커피", "디저트", "케이크", "베이커리");
        addCategoryPosition(positioned, orderSource, "BAR", "술집", "술", "이자카야", "포차", "와인", "맥주", "하이볼", "bar");
        addCategoryPosition(positioned, orderSource, "KARAOKE", "코노", "코인노래방", "노래방", "노래연습장");
        addCategoryPosition(positioned, orderSource, "SHOPPING", "쇼핑", "편집샵", "소품샵", "서점", "백화점", "아울렛", "쇼핑몰", "구경");
        addCategoryPosition(positioned, orderSource, "ACTIVITY", "놀거리", "전시", "산책", "보드게임", "방탈출", "체험", "영화", "vr", "오락실", "여행", "관광");

        positioned.stream()
                .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                .map(CategoryPosition::categoryKey)
                .forEach(resolved::add);

        boolean excludeCafe = isExplicitNone(cafeHint)
                || containsAny(normalizedHint, "카페안가", "카페안", "카페말고", "카페없이", "커피안", "커피없이")
                || containsAny(avoidHint, "카페제외", "커피제외");

        if (containsAny(firstNonBlank(restaurantHint, categorySource), "밥", "식사", "점심", "저녁", "한식", "중식", "일식", "양식", "고기", "고깃집", "파스타", "삼겹살", "갈비", "메뉴", "맛집")) {
            resolved.add("RESTAURANT");
        }
        if (!excludeCafe && containsAny(firstNonBlank(cafeHint, categorySource), "카페", "커피", "디저트", "브런치", "케이크", "베이커리")) {
            resolved.add("CAFE");
        }
        if (containsAny(categorySource, "술", "술집", "이자카야", "포차", "와인", "맥주", "하이볼", "bar")) {
            resolved.add("BAR");
        }
        if (containsAny(categorySource, "pc방", "피시방", "게임", "롤", "발로란트", "오버워치")) {
            resolved.add("PC_ROOM");
        }
        if (containsAny(categorySource, "코노", "코인노래방", "노래방", "노래연습장")) {
            resolved.add("KARAOKE");
        }
        if (containsAny(categorySource, "쇼핑", "편집샵", "소품샵", "서점", "백화점", "아울렛", "쇼핑몰", "구경")) {
            resolved.add("SHOPPING");
        }
        if (containsAny(categorySource, "놀", "놀거리", "전시", "산책", "보드게임", "방탈출", "체험", "영화", "vr", "오락실", "여행", "관광")) {
            resolved.add("ACTIVITY");
        }

        if (resolved.isEmpty()) {
            resolved.addAll(DEFAULT_CATEGORY_ORDER);
        }

        if (resolved.size() == 1) {
            String only = resolved.iterator().next();
            if ("BAR".equals(only) || "PC_ROOM".equals(only) || "KARAOKE".equals(only)) {
                resolved.add("RESTAURANT");
            } else if ("RESTAURANT".equals(only) && !excludeCafe) {
                resolved.add("CAFE");
            } else if ("ACTIVITY".equals(only) && !excludeCafe) {
                resolved.add("CAFE");
            } else if ("SHOPPING".equals(only) && !excludeCafe) {
                resolved.add("CAFE");
            }
        }

        return new ArrayList<>(resolved);
    }

    private String normalizeSearchHint(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("\\s+", "");
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (!needle.isBlank() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String extractLabeledHint(String planHint, String label) {
        if (planHint == null || planHint.isBlank()) {
            return "";
        }

        for (String line : planHint.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(label + ":")) {
                return trimmed.substring((label + ":").length()).trim();
            }
        }

        return "";
    }

    private boolean isExplicitNone(String value) {
        String normalized = normalizeSearchHint(value);
        return !normalized.isBlank() && containsAny(normalized, "없음", "없어", "안가", "안감", "제외", "말고");
    }

    private void addCategoryPosition(List<CategoryPosition> target, String source, String categoryKey, String... needles) {
        int index = firstIndexOfAny(source, needles);
        if (index >= 0) {
            target.add(new CategoryPosition(categoryKey, index));
        }
    }

    private int firstIndexOfAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        int found = Integer.MAX_VALUE;
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            int index = value.indexOf(needle);
            if (index >= 0 && index < found) {
                found = index;
            }
        }
        return found == Integer.MAX_VALUE ? -1 : found;
    }

    private List<PlaceRecommendResponse> fallbackPlaceRecommendations(PlaceRecommendRequest request) {
        String meetingType = valueOrDefault(request.getMeetingType(), "모임");
        String preferenceSummary = request.getPreferences() == null || request.getPreferences().isEmpty()
                ? "편하게 대화하기 좋은"
                : String.join(", ", request.getPreferences());

        return List.of(
                PlaceRecommendResponse.builder()
                        .name(meetingType + " 추천 장소 1")
                        .reason(preferenceSummary + " 분위기에 맞는 기본 추천입니다.")
                        .build(),
                PlaceRecommendResponse.builder()
                        .name(meetingType + " 추천 장소 2")
                        .reason("접근성이 좋고 일정 잡기 쉬운 장소를 우선 추천했습니다.")
                        .build(),
                PlaceRecommendResponse.builder()
                        .name(meetingType + " 추천 장소 3")
                        .reason("무난하게 모이기 좋은 후보를 기본값으로 제공했습니다.")
                        .build()
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record RouteScenario(
            String title,
            String summary,
            String fitReason,
            List<String> categoryOrder
    ) {
    }

    private record CategoryPosition(
            String categoryKey,
            int index
    ) {
    }
}
