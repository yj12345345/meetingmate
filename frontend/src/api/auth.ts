import http from "./http";
import type { ApiResponse } from "../types/api";

type LoginPayload = {
    accessToken: string;
};

export const signupLocal = async (payload: {
    email: string;
    password: string;
    nickname: string;
}) => {
    await http.post<ApiResponse<null>>("/api/auth/signup", payload);
};

export const loginLocal = async (payload: {
    email: string;
    password: string;
}) => {
    const res = await http.post<ApiResponse<LoginPayload>>("/api/auth/login", payload);
    return res.data.data.accessToken;
};

/** Revokes this login's refresh token on the server and clears the cookie. Best effort. */
export const logoutRemote = async () => {
    try {
        await http.post("/api/auth/logout");
    } catch {
        // Logging out locally must work even if the server is unreachable.
    }
};
