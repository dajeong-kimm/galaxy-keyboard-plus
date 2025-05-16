import axios from "axios";

const axiosInstance = axios.create({
  baseURL: "https://k12e201.p.ssafy.io",
  headers: {
    // "Content-Type": "application/x-www-form-urlencoded",
    // Accept: "application/json",
    "X-Bypass-Auth": "adminadmin",
  },
  withCredentials: true,
});

export default axiosInstance;
