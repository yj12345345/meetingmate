import type {
    KeywordRecommendation,
    KeywordRecommendationCategory,
    KeywordRecommendationRoute,
    ResolvedKakaoPlace,
} from "../types/recommendation";

type RecommendationCacheQuery = {
    keyword: string;
    meetingType?: string;
    mood?: string;
    location?: string;
    planHint?: string;
    maxDistanceKm?: number;
};

export type RecommendationCacheEntry = {
    categories: KeywordRecommendationCategory[];
    routes: KeywordRecommendationRoute[];
    source: string;
    warning?: string | null;
    selectedPlaces: Record<string, KeywordRecommendation>;
    resolvedPlaces: Record<string, ResolvedKakaoPlace>;
    savedAt: number;
};

const PREFIX = "meetingmate:recommendation:v9";

const getUserScope = () => {
    const token = localStorage.getItem("accessToken");
    return token ? token.slice(-16) : "guest";
};

const normalizePart = (value?: string) => (value || "").trim();
const normalizeDistancePart = (value?: number) =>
    Number.isFinite(value) ? String(Math.round((value || 0) * 10) / 10) : "";

export const buildRecommendationCacheKey = (query: RecommendationCacheQuery) =>
    [
        PREFIX,
        getUserScope(),
        normalizePart(query.keyword),
        normalizePart(query.meetingType),
        normalizePart(query.mood),
        normalizePart(query.location),
        normalizePart(query.planHint),
        normalizeDistancePart(query.maxDistanceKm),
    ].join("::");

export const loadRecommendationCache = (
    cacheKey: string,
): RecommendationCacheEntry | null => {
    const raw = localStorage.getItem(cacheKey);
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw) as RecommendationCacheEntry;
    } catch {
        localStorage.removeItem(cacheKey);
        return null;
    }
};

export const saveRecommendationCache = (
    cacheKey: string,
    entry: RecommendationCacheEntry,
) => {
    localStorage.setItem(cacheKey, JSON.stringify(entry));
};

export const clearRecommendationCache = (cacheKey: string) => {
    localStorage.removeItem(cacheKey);
};
