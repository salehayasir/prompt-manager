import axios from "axios";

export const promptApi = axios.create({
    baseURL: "http://localhost:8000"
});

export const reviewApi = axios.create({
    baseURL: "http://localhost:8001"
});