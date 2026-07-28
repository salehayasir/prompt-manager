import axios from "axios";
import { getToken, clearToken } from "./authToken";

export const authApi = axios.create({
    baseURL: "/api/auth"
});

export const promptApi = axios.create({
    baseURL: "/api/prompts"
});

export const reviewApi = axios.create({
    baseURL: "/api/reviews"
});

function attachAuth(instance) {

    instance.interceptors.request.use((config) => {
        const token = getToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    });

    instance.interceptors.response.use(
        (response) => response,
        (error) => {
            if (error.response?.status === 401) {
                clearToken();
            }
            return Promise.reject(error);
        }
    );
}

attachAuth(promptApi);
attachAuth(reviewApi);