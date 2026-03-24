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

export const recommendPlaces = async (req: RecommendRequest) => {
    const res = await http.post<ApiResponse<PlaceRecommendation[]>>(
        "/api/recommendations",
        req
    );
    return res.data.data;
};

export const recommendByKeyword = async (params: KeywordRecommendRequest) => {
    const res = await http.get<ApiResponse<KeywordRecommendResult>>("/api/recommend", {
        params,
    });
    return res.data.data;
};

export const recommendRoutesFromSelections = async (
    request: SelectedRouteRecommendRequest,
) => {
    const res = await http.post<ApiResponse<SelectedRouteRecommendResult>>(
        "/api/recommend/selected-routes",
        request,
    );
    return res.data.data;
};
