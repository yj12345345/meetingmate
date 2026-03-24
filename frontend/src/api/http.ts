import axios from "axios";

const http = axios.create({
    // Use same-origin in production and rely on nginx reverse proxy.
    baseURL: "/",
});

http.interceptors.request.use((config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default http;
