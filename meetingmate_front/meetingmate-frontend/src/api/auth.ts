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
