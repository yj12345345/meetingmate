import http from "./http";
import type { ApiResponse } from "../types/api";
import type { UserMe } from "../types/user";

export const getMyInfo = async () => {
    const res = await http.get<ApiResponse<UserMe>>("/api/user/me");
    return res.data.data;
};

export const updateMyProfile = async (payload: { nickname: string }) => {
    const res = await http.put<ApiResponse<UserMe>>("/api/user/profile", payload);
    return res.data.data;
};

export const deleteMyAccount = async () => {
    await http.delete<ApiResponse<null>>("/api/user");
};
