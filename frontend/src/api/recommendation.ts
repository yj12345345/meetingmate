import http from "./http";
import type {
    KeywordRecommendRequest,
    KeywordRecommendResult,
    PlaceRecommendation,
    RecommendRequest,
    SelectedRouteRecommendRequest,
    SelectedRouteRecommendResult,
} from "../types/recommendation";
import type { ApiResponse } from "../types/api";

const SESSION_EXPIRED_MESSAGE = "로그인이 만료되었거나 인증이 필요합니다. 다시 로그인해 주세요.";

const unwrapApiResponse = <T>(payload: unknown, fallbackMessage: string): T => {
    if (typeof payload === "string") {
        const normalized = payload.toLowerCase();
        if (normalized.includes("<html") || normalized.includes("<!doctype")) {
            throw new Error(SESSION_EXPIRED_MESSAGE);
        }
        throw new Error(fallbackMessage);
    }

    if (!payload || typeof payload !== "object") {
        throw new Error(fallbackMessage);
    }

    const envelope = payload as Partial<ApiResponse<T>>;
    if (envelope.success !== true) {
        throw new Error(envelope.message || fallbackMessage);
    }

    if (envelope.data === undefined || envelope.data === null) {
        throw new Error(envelope.message || fallbackMessage);
    }

    return envelope.data;
};

export const recommendPlaces = async (req: RecommendRequest) => {
    const res = await http.post<ApiResponse<PlaceRecommendation[]>>(
        "/api/recommendations",
        req
    );
    return unwrapApiResponse<PlaceRecommendation[]>(
        res.data,
        "추천 장소를 불러오지 못했습니다.",
    );
};

export const recommendByKeyword = async (params: KeywordRecommendRequest) => {
    const res = await http.get<ApiResponse<KeywordRecommendResult>>("/api/recommend", {
        params,
    });
    return unwrapApiResponse<KeywordRecommendResult>(
        res.data,
        "추천 결과를 불러오지 못했습니다.",
    );
};

export const recommendRoutesFromSelections = async (
    request: SelectedRouteRecommendRequest,
) => {
    const res = await http.post<ApiResponse<SelectedRouteRecommendResult>>(
        "/api/recommend/selected-routes",
        request,
    );
    return unwrapApiResponse<SelectedRouteRecommendResult>(
        res.data,
        "선택 장소 기반 코스를 불러오지 못했습니다.",
    );
};
