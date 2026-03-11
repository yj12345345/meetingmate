import axios from "axios";

const http = axios.create({
    baseURL: "http://54.206.113.147",
});

http.interceptors.request.use((config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default http;