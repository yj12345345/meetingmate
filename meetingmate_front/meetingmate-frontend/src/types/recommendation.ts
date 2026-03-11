export interface PlaceRecommendation {
    name: string;
    reason: string;
}

export type RecommendationCategoryKey =
    | "RESTAURANT"
    | "CAFE"
    | "BAR"
    | "PC_ROOM"
    | "KARAOKE"
    | "SHOPPING"
    | "ACTIVITY"
    | string;

export interface KeywordRecommendation {
    category?: RecommendationCategoryKey;
    name: string;
    address: string;
    reason?: string;
    tags?: string[];
    mapQuery?: string;
}

export interface ResolvedKakaoPlace {
    placeKey: string;
    lat: number;
    lng: number;
    detailUrl: string;
    displayName: string;
    roadAddress?: string;
    addressName?: string;
    phone?: string;
    categoryName?: string;
    matchedQuery?: string;
}

export interface KeywordRecommendationCategory {
    key: RecommendationCategoryKey;
    title: string;
    description?: string;
    places: KeywordRecommendation[];
}

export interface KeywordRecommendationRouteStep {
    order: number;
    category?: RecommendationCategoryKey;
    placeName: string;
    address: string;
    reason?: string;
    mapQuery?: string;
}

export interface KeywordRecommendationRoute {
    title: string;
    summary?: string;
    fitReason?: string;
    steps: KeywordRecommendationRouteStep[];
}

export interface KeywordRecommendResult {
    recommendations: KeywordRecommendation[];
    categories: KeywordRecommendationCategory[];
    routes: KeywordRecommendationRoute[];
    source: "AI" | "DB" | "FALLBACK" | string;
    warning?: string | null;
}

export interface RecommendRequest {
    meetingType: string;
    category: string;
    preferences: string[];
}

export interface KeywordRecommendRequest {
    keyword: string;
    meetingType?: string;
    mood?: string;
    location?: string;
    planHint?: string;
}

export interface SelectedRouteRecommendRequest extends KeywordRecommendRequest {
    selectedPlaces: KeywordRecommendation[];
}

export interface SelectedRouteRecommendResult {
    selectedPlaces: KeywordRecommendation[];
    routes: KeywordRecommendationRoute[];
    source: "AI" | "FALLBACK" | string;
    warning?: string | null;
}
