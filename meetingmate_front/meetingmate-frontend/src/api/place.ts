import http from "./http";
import type { Place } from "../types/place";

export const getPlaces = (groupId: number) => {
    return http.get<Place[]>(`/api/groups/${groupId}/places`);
};

export const confirmPlace = (groupId: number, placeId: number) => {
    return http.post(`/api/groups/${groupId}/places/${placeId}/confirm`);
};