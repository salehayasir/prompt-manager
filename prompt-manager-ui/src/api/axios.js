import axios from "axios";

export const promptApi = axios.create({
    baseURL: "/api/prompts"
});

export const reviewApi = axios.create({
    baseURL: "/api/reviews"
});