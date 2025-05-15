import axios from "axios";

const axiosInstance = axios.create({
  baseURL: "http://k12e201.p.ssafy.io:8091",
  headers: {
    "Content-Type": "application/x-www-form-urlencoded",
    Accept: "application/json",
  },
});

export default axiosInstance;
