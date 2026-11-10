import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import {
    buildRedirectTarget,
    clearAccessToken,
    getAccessToken,
    savePostLoginRedirect,
    setAccessToken,
} from "../utils/auth";

const http = axios.create({
    // Use same-origin in production and rely on nginx reverse proxy.
    baseURL: "/",
    // The refresh token is an HttpOnly cookie scoped to /api/auth; same-origin requests
    // send it automatically, this just makes the intent explicit.
    withCredentials: true,
});

http.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Endpoints whose 401 must not trigger a refresh (they ARE the auth flow).
const AUTH_PATHS = ["/api/auth/login", "/api/auth/signup", "/api/auth/refresh", "/api/auth/logout"];

// Single-flight: if several requests get 401 at once (e.g. a page loading in parallel),
// they all wait for ONE refresh call. Without this each would rotate the refresh token,
// and every rotation after the first would look like token reuse to the server.
let refreshInFlight: Promise<string> | null = null;

export const refreshAccessToken = (): Promise<string> => {
    if (!refreshInFlight) {
        refreshInFlight = axios
            .post("/api/auth/refresh", null, { withCredentials: true })
            .then((res) => {
                const token: string = res.data.data.accessToken;
                setAccessToken(token);
                return token;
            })
            .finally(() => {
                refreshInFlight = null;
            });
    }
    return refreshInFlight;
};

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

http.interceptors.response.use(
    (res) => res,
    async (error: AxiosError) => {
        const original = error.config as RetriableConfig | undefined;
        const url = original?.url ?? "";
        const isAuthCall = AUTH_PATHS.some((path) => url.startsWith(path));

        if (error.response?.status !== 401 || !original || original._retried || isAuthCall) {
            return Promise.reject(error);
        }

        original._retried = true;
        try {
            const token = await refreshAccessToken();
            original.headers.Authorization = `Bearer ${token}`;
            return http(original);
        } catch (refreshError) {
            // Refresh token missing/expired/revoked: the session is really over.
            // Remember where the user was so login can bring them back here.
            clearAccessToken();
            const { pathname, search, hash } = window.location;
            savePostLoginRedirect(buildRedirectTarget(pathname, search, hash));
            window.location.assign("/login");
            return Promise.reject(refreshError);
        }
    },
);

export default http;
