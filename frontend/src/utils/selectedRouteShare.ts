import type { KeywordRecommendation } from "../types/recommendation";

export type SelectedRouteShareState = {
    keyword?: string;
    meetingType?: string;
    mood?: string;
    location?: string;
    planHint?: string;
    selectedPlaces?: KeywordRecommendation[];
};

type EncodedSelectedPlace = {
    c?: string;
    n: string;
    a: string;
    q?: string;
};

type EncodedSharePayload = {
    k?: string;
    t?: string;
    m?: string;
    l?: string;
    p?: string;
    s: EncodedSelectedPlace[];
};

const toBase64Url = (value: string) => {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    bytes.forEach((byte) => {
        binary += String.fromCharCode(byte);
    });
    return btoa(binary)
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
};

const fromBase64Url = (value: string) => {
    const base64 = value
        .replace(/-/g, "+")
        .replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4 || 4)) % 4);
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return new TextDecoder().decode(bytes);
};

export const encodeSelectedRouteShareState = (state: SelectedRouteShareState) => {
    const payload: EncodedSharePayload = {
        k: state.keyword || undefined,
        t: state.meetingType || undefined,
        m: state.mood || undefined,
        l: state.location || undefined,
        p: state.planHint || undefined,
        s: (state.selectedPlaces || [])
            .filter((item) => Boolean(item?.name && item?.address))
            .map((item) => ({
                c: item.category || undefined,
                n: item.name,
                a: item.address,
                q: item.mapQuery || undefined,
            })),
    };
    return toBase64Url(JSON.stringify(payload));
};

export const decodeSelectedRouteShareState = (token: string | null): SelectedRouteShareState | null => {
    if (!token) {
        return null;
    }

    try {
        const raw = JSON.parse(fromBase64Url(token)) as Partial<EncodedSharePayload>;
        const selectedPlaces = Array.isArray(raw.s)
            ? raw.s
                .filter((item) => Boolean(item?.n && item?.a))
                .map((item) => ({
                    category: item.c,
                    name: item.n,
                    address: item.a,
                    mapQuery: item.q,
                }))
            : [];

        return {
            keyword: raw.k || "",
            meetingType: raw.t || "",
            mood: raw.m || "",
            location: raw.l || "",
            planHint: raw.p || "",
            selectedPlaces,
        };
    } catch {
        return null;
    }
};

export const buildSelectedRouteShareUrl = (state: SelectedRouteShareState, origin: string) => {
    const token = encodeSelectedRouteShareState(state);
    return `${origin}/selected-route?s=${encodeURIComponent(token)}`;
};

